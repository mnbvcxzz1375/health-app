# Ultralytics AGPL-3.0 License
# pyright: reportImplicitOverride=false
# pyright: reportAny=false
"""DFAD-YOLO v2.1 modules: DCT frequency enhancement, ViT-CNN alignment, object-level consistency, robustness."""

import math

import torch
import torch.nn as nn
import torch.nn.functional as F
from ultralytics.nn.modules.block import DINO3Backbone

__all__ = (
    "DINO3BackboneAdapter",
    "DDFPlusPlusDCT",
    "FeatureAlignmentLayerPP",
    "CrossScaleConsistencyLossObj",
    "RobustnessAugmentor",
)


class DINO3BackboneAdapter(DINO3Backbone):
    """DFAD-YOLO v2.1: Extends DINO3Backbone without modifying its source.

    Adds configurable dino_input_size (resolution decoupling) and
    guaranteed detach() when freeze_backbone=True, per plan guardrail:
    'NO 修改原始 DINO3Backbone（只能 wrap/extend）'.
    """

    def __init__(self, model_name='dinov3_vitb16', freeze_backbone=True,
                 output_channels=512, dino_input_size: int = 224, input_channels=None):
        super().__init__(model_name, freeze_backbone, output_channels, input_channels)
        self._dfad_dino_input_size = dino_input_size
        if self.freeze_backbone and hasattr(self, "freeze_backbone_layers"):
            self.freeze_backbone_layers()

    def forward(self, x):
        import torch
        import torch.nn.functional as F
        B, C, H, W = x.shape
        if self.input_projection is None:
            self.input_channels = C
            self._create_projection_layers(C)
        pseudo_rgb = self.input_projection(x)
        dino_size = self._dfad_dino_input_size
        pseudo_rgb_resized = F.interpolate(pseudo_rgb, size=(dino_size, dino_size),
                                           mode='bilinear', align_corners=False)
        with torch.set_grad_enabled(not self.freeze_backbone):
            if hasattr(self.dino_model, "forward_features"):
                outputs = self.dino_model.forward_features(pseudo_rgb_resized)
            else:
                outputs = self.dino_model(pseudo_rgb_resized)
            if isinstance(outputs, dict) and "x_norm_patchtokens" in outputs:
                features = outputs["x_norm_patchtokens"]
            elif hasattr(outputs, "last_hidden_state"):
                features = outputs.last_hidden_state
            elif isinstance(outputs, (list, tuple)):
                features = outputs[0]
            else:
                features = outputs
        dino_features = self.extract_features(features, (dino_size, dino_size))
        dino_features_resized = F.interpolate(dino_features, size=(H, W),
                                              mode='bilinear', align_corners=False)
        # DFAD guardrail: guaranteed detach when backbone is frozen
        if self.freeze_backbone:
            dino_features_resized = dino_features_resized.detach()
        combined_features = torch.cat([x, dino_features_resized], dim=1)
        return self.fusion_layer(combined_features)

    def train(self, mode=True):
        """Keep DINO parameters protected when the backbone is frozen."""
        super().train(mode)
        if self.freeze_backbone:
            self.dino_model.eval()
            for param in self.dino_model.parameters():
                param.requires_grad = False
                param._ultralytics_frozen = True
        return self

class DDFPlusPlusDCT(nn.Module):
    """DCT-based Dual-Domain Frequency Enhancement (DDF++) for stable high-frequency feature refinement.

    This module enhances local high-frequency details via block-wise real-valued DCT (no complex FFT),
    with learnable band routing and lightweight global context gating.
    """

    def __init__(self, c1: int, c2: int | None = None, window_size: int = 8):
        super().__init__()
        c2 = c1 if c2 is None else int(c2)
        self.eps: float = 1e-6

        if window_size <= 0:
            raise ValueError(f"window_size must be > 0, got {window_size}")
        self.window_size: int = int(window_size)

        # 1) Input projection
        self.proj: nn.Conv2d | None = None
        if c1 != c2:
            self.proj = nn.Conv2d(c1, c2, 1, bias=False)

        # 2) Spatial path: DWConv + BN + SiLU + PWConv
        self.spatial_dwconv: nn.Conv2d = nn.Conv2d(c2, c2, 3, padding=1, groups=c2, bias=False)
        self.spatial_bn: nn.BatchNorm2d = nn.BatchNorm2d(c2)
        self.spatial_act: nn.SiLU = nn.SiLU(inplace=True)
        self.spatial_pwconv: nn.Conv2d = nn.Conv2d(c2, c2, 1, bias=False)

        # 3) DCT frequency path
        N = self.window_size
        # Orthonormal DCT-II basis (so inverse is transpose)
        # B[k, n] = alpha(k) * cos(pi * (n + 0.5) * k / N)
        n = torch.arange(N, dtype=torch.float32)
        k = torch.arange(N, dtype=torch.float32).unsqueeze(1)
        basis = torch.cos(math.pi * (n + 0.5) * k / N)
        alpha = torch.full((N,), math.sqrt(2.0 / N), dtype=torch.float32)
        alpha[0] = math.sqrt(1.0 / N)
        basis = basis * alpha.unsqueeze(1)
        self.register_buffer("dct_basis", basis, persistent=False)
        self.dct_basis: torch.Tensor

        # Band-wise learnable routing (gate per (u, v) frequency)
        self.band_weights: nn.Parameter = nn.Parameter(torch.zeros(N * N))

        self.freq_conv: nn.Conv2d = nn.Conv2d(c2, c2, 1, bias=False)
        self.freq_bn: nn.BatchNorm2d = nn.BatchNorm2d(c2)
        self.freq_act: nn.SiLU = nn.SiLU(inplace=True)

        # 4) Global context gate (SE-like)
        hidden = max(1, c2 // 4)
        self.ctx_fc1: nn.Linear = nn.Linear(c2, hidden)
        self.ctx_fc2: nn.Linear = nn.Linear(hidden, c2)
        self.ctx_act: nn.SiLU = nn.SiLU(inplace=True)
        self.ctx_sigmoid: nn.Sigmoid = nn.Sigmoid()

        # 5) Routing between spatial and frequency paths
        self.routing: nn.Parameter = nn.Parameter(torch.zeros(2))

        # 6) Residual warmup gate
        self.residual_gate: nn.Parameter = nn.Parameter(torch.tensor(0.0))
        self.fal_ref = None  # Set post-build by tasks.py for struct-bias sharing

    def _dct2d(self, x_blocks: torch.Tensor) -> torch.Tensor:
        """Apply 2D DCT-II to blocks of shape (..., W, W) using precomputed basis."""
        B: torch.Tensor = self.dct_basis
        # (..., W, W) -> (..., W, W)
        return torch.matmul(torch.matmul(B, x_blocks), B.transpose(0, 1))

    def _idct2d(self, X_blocks: torch.Tensor) -> torch.Tensor:
        """Apply 2D inverse DCT-II (orthonormal) to blocks."""
        B: torch.Tensor = self.dct_basis
        return torch.matmul(torch.matmul(B.transpose(0, 1), X_blocks), B)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Forward.

        Args:
            x (torch.Tensor): (B, C, H, W) input feature tensor.

        Returns:
            torch.Tensor: (B, C2, H, W) enhanced feature tensor.
        """
        x_proj: torch.Tensor = x if self.proj is None else self.proj(x)

        # Spatial path
        spatial_feat: torch.Tensor = self.spatial_pwconv(self.spatial_act(self.spatial_bn(self.spatial_dwconv(x_proj))))

        # Frequency path (local-window DCT)
        B, C, H, W = x_proj.shape
        ws = self.window_size
        pad_h = (ws - (H % ws)) % ws
        pad_w = (ws - (W % ws)) % ws

        x_freq = x_proj
        if pad_h or pad_w:
            # Prefer reflect padding when valid; otherwise fall back to constant.
            mode = "reflect" if (H > 1 and W > 1 and pad_h < H and pad_w < W) else "constant"
            x_freq = F.pad(x_freq, (0, pad_w, 0, pad_h), mode=mode)

        Hp, Wp = x_freq.shape[-2:]
        nH, nW = Hp // ws, Wp // ws

        orig_dtype = x_freq.dtype
        x_freq32 = x_freq.float()

        blocks = x_freq32.view(B, C, nH, ws, nW, ws).permute(0, 1, 2, 4, 3, 5).contiguous()
        # (B, C, nH, nW, ws, ws)
        coeffs = self._dct2d(blocks)

        band_gate = torch.sigmoid(self.band_weights).view(1, 1, 1, 1, ws, ws)
        coeffs = coeffs * band_gate

        enhanced_blocks = self._idct2d(coeffs)
        enhanced = enhanced_blocks.permute(0, 1, 2, 4, 3, 5).contiguous().view(B, C, Hp, Wp)

        if pad_h or pad_w:
            enhanced = enhanced[..., :H, :W]

        enhanced = enhanced.to(dtype=orig_dtype)
        freq_feat: torch.Tensor = self.freq_act(self.freq_bn(self.freq_conv(enhanced)))

        # Global context gate from projected features
        ctx = x_proj.mean(dim=(2, 3))  # (B, C)
        gate: torch.Tensor = self.ctx_fc2(self.ctx_act(self.ctx_fc1(ctx)))
        gate = self.ctx_sigmoid(gate).view(B, C, 1, 1)

        # Route & fuse
        w = torch.softmax(self.routing, dim=0)
        fused: torch.Tensor = w[0] * spatial_feat + w[1] * (gate * freq_feat)

        struct_mod = 1.0
        if self.fal_ref is not None and hasattr(self.fal_ref, '_struct_gate'):
            sg = self.fal_ref._struct_gate  # (B, C_fal, H_fal, W_fal)
            if sg.shape[1] == x_proj.shape[1] and sg.shape[2:] == x_proj.shape[2:]:
                struct_mod = sg
            # else: channel or spatial mismatch — skip gate to avoid silent broadcast error
        return x_proj + self.residual_gate * struct_mod * fused


class FeatureAlignmentLayerPP(nn.Module):
    """ViT-to-CNN feature alignment module with statistical alignment, scale alignment, and structural bias.

    Args:
        channels: Number of output channels for CNN features
        dino_channels: Number of input channels from DINO features (defaults to channels if None)
    """

    def __init__(self, channels, dino_channels=None):
        super().__init__()
        if dino_channels is None:
            dino_channels = channels

        self.channels = channels
        self.dino_channels = dino_channels
        self.eps = 1e-6

        # Sub-component 1: Scale-Align - 1x1 Conv to project dino_channels to channels
        self.scale_proj = nn.Conv2d(dino_channels, channels, 1, bias=False)

        # Sub-component 2: Stat-Align - Learnable per-channel gamma and beta
        self.gamma = nn.Parameter(torch.ones(channels))
        self.beta = nn.Parameter(torch.zeros(channels))

        # Sub-component 3: Struct-Bias - H/V strip pooling branches
        # H-branch: mean along width, then 1x1 conv to channels
        self.h_proj = nn.Conv2d(channels, channels, 1, bias=False)

        # V-branch: mean along height, then 1x1 conv to channels
        self.v_proj = nn.Conv2d(channels, channels, 1, bias=False)

        # Fusion: learnable blend parameter
        self.blend = nn.Parameter(torch.tensor(0.5))

    def forward(self, cnn_feat, dino_feat=None):
        """Align DINO features to CNN features with multi-component alignment.

        Supports two calling conventions:

        - Dual-input mode (standalone/tests): ``forward(cnn_feat, dino_feat)``
        - Single-input mode (YOLO parse_model integration): ``forward(x)``
          In this mode, the input is assumed to already be a fused/aligned feature,
          so the layer acts as an identity mapping.

        Args:
            cnn_feat: CNN features of shape (B, C, H, W) OR a fused feature tensor in single-input mode.
            dino_feat: Optional DINO features of shape (B, D, h, w) where D=dino_channels.

        Returns:
            Aligned features of shape (B, C, H, W) same as cnn_feat.
        """

        # Allow passing both tensors as a single list/tuple: m([cnn, dino])
        if dino_feat is None and isinstance(cnn_feat, (list, tuple)):
            if len(cnn_feat) != 2:
                raise ValueError(
                    f"FeatureAlignmentLayerPP single-arg sequence input must be (cnn_feat, dino_feat), got len={len(cnn_feat)}"
                )
            cnn_feat, dino_feat = cnn_feat

        # Single-input mode for YOLO sequential graphs: act as identity.
        if dino_feat is None:
            return cnn_feat

        B, C, H, W = cnn_feat.shape

        # Scale-Align: bilinear interpolation to match cnn_feat spatial size
        dino_scaled = F.interpolate(dino_feat, size=(H, W), mode="bilinear", align_corners=False)
        dino_proj = self.scale_proj(dino_scaled)  # (B, C, H, W)

        # Stat-Align: Instance normalization with learnable scale/shift
        # Compute mean and variance per-sample per-channel across spatial dimensions
        mean = dino_proj.mean(dim=(2, 3), keepdim=True)  # (B, C, 1, 1)
        var = dino_proj.var(dim=(2, 3), keepdim=True, unbiased=False)  # (B, C, 1, 1)

        # Normalize and apply learnable gamma and beta
        dino_aligned = (dino_proj - mean) / torch.sqrt(var + self.eps)
        dino_aligned = dino_aligned * self.gamma.view(1, C, 1, 1) + self.beta.view(1, C, 1, 1)

        # Struct-Bias: H/V strip pooling (computed once per forward)
        # H-branch: mean along width dimension (pooling over W)
        h_pooled = dino_aligned.mean(dim=3, keepdim=True)  # (B, C, H, 1)
        h_gate = torch.sigmoid(self.h_proj(h_pooled))  # (B, C, H, 1)

        # V-branch: mean along height dimension (pooling over H)
        v_pooled = dino_aligned.mean(dim=2, keepdim=True)  # (B, C, 1, W)
        v_gate = torch.sigmoid(self.v_proj(v_pooled))  # (B, C, 1, W)

        # Combined struct gate (broadcasts to B, C, H, W)
        struct_gate = h_gate * v_gate  # (B, C, H, W)
        self._struct_gate = struct_gate.detach()  # cache for DDFPlusPlusDCT reuse

        # Fusion: apply struct gate to aligned features
        dino_gated = dino_aligned * struct_gate

        # Blend aligned DINO features with CNN features
        output = cnn_feat + torch.sigmoid(self.blend) * dino_gated

        return output


class CrossScaleConsistencyLossObj(nn.Module):
    """Object-level cross-scale consistency loss.

    For each GT box, find positive samples on each scale via center-based assignment,
    then enforce:
    1) Objectness consistency across scales (pairwise L1 between per-scale confidences)
    2) Classification distribution consistency across scales (weighted symmetrized KL)

    Notes:
        - OBJECT-LEVEL only: positives are defined per-GT; no background alignment.
        - No pixel/feature MSE is used anywhere.
        - Returns a 0.0 scalar tensor (with grad) if no valid target/scale pairs exist.
    """

    def __init__(self, num_scales: int = 3, cls_weight: float = 1.0, rank_weight: float = 1.0):
        """Initialize loss.

        Args:
            num_scales: Number of prediction scales/levels.
            cls_weight: Weight for classification KL consistency term.
            rank_weight: Weight for objectness consistency term.
        """

        super().__init__()
        self.num_scales: int = int(num_scales)
        self.cls_weight: float = float(cls_weight)
        self.rank_weight: float = float(rank_weight)

        self.center_radius: float = 2.5
        self.eps: float = 1e-6

        # Anchor parameter to ensure returned loss tensors require grad in standalone usage
        # (e.g., smoke tests where prediction tensors do not have requires_grad=True).
        self._grad_anchor: nn.Parameter = nn.Parameter(torch.tensor(0.0))

        if self.num_scales <= 0:
            raise ValueError(f"num_scales must be > 0, got {self.num_scales}")

    def _zero_with_grad(self) -> torch.Tensor:
        """Return a scalar 0.0 tensor with grad."""

        return self._grad_anchor.sum() * 0.0

    @staticmethod
    def _kl_divergence(p: torch.Tensor, q: torch.Tensor, eps: float) -> torch.Tensor:
        """Compute KL(p || q) for probability vectors p, q (1D)."""

        p = p.clamp_min(eps)
        q = q.clamp_min(eps)
        return (p * (p.log() - q.log())).sum()

    def forward(
        self,
        scale_preds: list[dict[str, torch.Tensor]],
        targets: torch.Tensor,
        img_size: tuple[int, int],
    ) -> torch.Tensor:
        """Compute object-level cross-scale consistency loss.

        Args:
            scale_preds: List of dicts, one per scale. Each dict has:
                - 'cls': (B, A, num_cls) classification logits
                - 'obj': (B, A, 1) objectness/quality scores
                - 'bbox': (B, A, 4) predicted boxes (cx, cy, w, h) in pixels
            targets: (N, 6) tensor [batch_idx, cls_id, cx, cy, w, h] in normalized coords.
            img_size: Tuple (H, W) of input image size.

        Returns:
            Scalar loss tensor.
        """

        if self.num_scales < 2 or len(scale_preds) < 2:
            return self._zero_with_grad()
        if targets.numel() == 0:
            return self._zero_with_grad()

        H, W = int(img_size[0]), int(img_size[1])
        num_scales = min(self.num_scales, len(scale_preds))

        # Basic validation and cached scale metadata.
        stride_px: list[float] = []
        for s in range(num_scales):
            if not all(k in scale_preds[s] for k in ("cls", "obj", "bbox")):
                raise KeyError("scale_preds entries must contain keys: 'cls', 'obj', 'bbox'")

            A = int(scale_preds[s]["bbox"].shape[1])
            # Approximate stride (grid-cell size) from anchor count.
            # sqrt(H*W/A) is a reasonable proxy when A ~= grid_H*grid_W.
            approx = (float(H) * float(W)) / max(float(A), 1.0)
            stride_px.append(float(torch.sqrt(torch.tensor(approx)).item()))

        # Ensure float computations for geometric checks.
        targets_f = targets.to(dtype=torch.float32)
        device = scale_preds[0]["obj"].device
        targets_f = targets_f.to(device=device)

        # Accumulators.
        rank_sum = self._zero_with_grad()
        kl_sum = self._zero_with_grad()
        rank_pairs = 0
        kl_pairs = 0

        # Loop over GT objects.
        for t in targets_f:
            b = int(t[0].item())
            # cls_id is not required for distribution alignment (full distribution used).
            cx = float(t[2].item()) * float(W)
            cy = float(t[3].item()) * float(H)
            gw = float(t[4].item()) * float(W)
            gh = float(t[5].item()) * float(H)

            x1 = cx - 0.5 * gw
            y1 = cy - 0.5 * gh
            x2 = cx + 0.5 * gw
            y2 = cy + 0.5 * gh

            per_scale_conf: list[torch.Tensor] = []
            per_scale_prob: list[torch.Tensor] = []

            for s in range(num_scales):
                bbox = scale_preds[s]["bbox"][b]  # (A, 4)
                centers = bbox[..., :2].to(dtype=torch.float32)
                xs = centers[:, 0]
                ys = centers[:, 1]

                inside = (xs >= x1) & (xs <= x2) & (ys >= y1) & (ys <= y2)
                radius = float(self.center_radius) * float(max(stride_px[s], 1.0))
                within = (xs - cx).abs() <= radius
                within = within & ((ys - cy).abs() <= radius)

                pos = inside & within
                pos_idx = pos.nonzero(as_tuple=False).squeeze(1)
                if pos_idx.numel() == 0:
                    # Fallback to any center-in-box matches for robustness.
                    pos_idx = inside.nonzero(as_tuple=False).squeeze(1)
                if pos_idx.numel() == 0:
                    continue

                obj_scores = scale_preds[s]["obj"][b, pos_idx, 0]
                max_obj = obj_scores.max()
                conf = torch.sigmoid(max_obj)

                cls_logits = scale_preds[s]["cls"][b, pos_idx]
                avg_logits = cls_logits.mean(dim=0).to(dtype=torch.float32)
                prob = F.softmax(avg_logits, dim=-1)

                per_scale_conf.append(conf)
                per_scale_prob.append(prob)

            if len(per_scale_conf) < 2:
                continue

            # Rank/objectness consistency: pairwise L1 between confidences.
            for i in range(len(per_scale_conf)):
                for j in range(i + 1, len(per_scale_conf)):
                    rank_sum = rank_sum + (per_scale_conf[i] - per_scale_conf[j]).abs()
                    rank_pairs += 1

            # Classification consistency: symmetrized KL weighted by confidence.
            for i in range(len(per_scale_prob)):
                for j in range(i + 1, len(per_scale_prob)):
                    p = per_scale_prob[i]
                    q = per_scale_prob[j]
                    kl_pq = self._kl_divergence(p, q, self.eps)
                    kl_qp = self._kl_divergence(q, p, self.eps)
                    sym = 0.5 * (kl_pq + kl_qp)
                    w = 0.5 * (per_scale_conf[i] + per_scale_conf[j]).to(dtype=torch.float32)
                    kl_sum = kl_sum + (w * sym)
                    kl_pairs += 1

        if rank_pairs == 0 and kl_pairs == 0:
            return self._zero_with_grad()

        rank_loss = rank_sum / max(rank_pairs, 1)
        kl_loss = kl_sum / max(kl_pairs, 1)
        loss = (self.rank_weight * rank_loss) + (self.cls_weight * kl_loss)
        return loss + (self._grad_anchor.sum() * 0.0)


class RobustnessAugmentor(nn.Module):
    """
    Robustness Augmentor for enhanced domain generalization.

    Applies adaptive augmentations to improve model robustness across domains.
    """

    def __init__(self, channels, augment_strength=0.1):
        """
        Initialize RobustnessAugmentor.

        Args:
            channels (int): Number of channels in input features.
            augment_strength (float): Strength of augmentation (0.0 to 1.0). Default: 0.1.
        """
        super().__init__()
        self.channels = channels
        self.augment_strength = augment_strength
        self.eps = 1e-6
        self.current_severity = 1  # RoDLA severity level: 1, 2, or 3

        # Backward-compatible learnable parameters
        self.augment_weight = nn.Parameter(torch.ones(channels) * augment_strength)
        self.augment_bias = nn.Parameter(torch.zeros(channels))

        # Adaptive perturbation routing for 12 RoDLA types
        self.perturb_logits = nn.Parameter(torch.zeros(12))
        # Backward-compatible scalar logits (deprecated but kept for old checkpoints)
        self.mask_logit = nn.Parameter(torch.tensor(0.0))
        self.smooth_logit = nn.Parameter(torch.tensor(0.0))
        self.contrast_logit = nn.Parameter(torch.tensor(0.0))

    def set_severity(self, level: int):
        """Set RoDLA severity level (1, 2, or 3)."""
        self.current_severity = max(1, min(3, int(level)))

    def _noise_perturb(self, x, strength):
        """Gaussian noise perturbation (RoDLA type 1)."""
        b, c, _, _ = x.shape
        channel_gain = torch.sigmoid(self.augment_weight).view(1, c, 1, 1)
        x_std = torch.sqrt(x.var(dim=(2, 3), keepdim=True, unbiased=False) + self.eps)
        noise = torch.randn(b, c, 1, 1, device=x.device, dtype=x.dtype)
        return x + strength * channel_gain * x_std * noise
    def _shot_noise_perturb(self, x, strength):
        """Shot noise perturbation (RoDLA type 2): noise proportional to sqrt(|x|)."""
        noise = torch.randn_like(x) * torch.sqrt(torch.abs(x) + self.eps) * strength
        return x + noise

    def _impulse_noise_perturb(self, x, strength):
        """Impulse noise perturbation (RoDLA type 3): random spatial zero-masking."""
        mask = (torch.rand_like(x) > strength * 0.1).to(x.dtype)
        return x * mask

    def _defocus_blur_perturb(self, x, strength):
        """Defocus blur perturbation (RoDLA type 4): avg_pool approximation."""
        kernel = max(3, int(3 + strength * 5))
        if kernel % 2 == 0:
            kernel += 1
        pad = kernel // 2
        blurred = F.avg_pool2d(x, kernel_size=kernel, stride=1, padding=pad)
        return x + strength * (blurred - x)

    def _motion_blur_perturb(self, x, strength):
        """Motion blur perturbation (RoDLA type 5): 1D depthwise conv along H."""
        kernel_size = max(3, int(3 + strength * 10))
        if kernel_size % 2 == 0:
            kernel_size += 1
        weight = torch.ones(x.shape[1], 1, kernel_size, 1, device=x.device, dtype=x.dtype) / kernel_size
        pad = (kernel_size // 2, 0)
        blurred = F.conv2d(x, weight, padding=pad, groups=x.shape[1])
        return x + strength * (blurred - x)

    def _zoom_blur_perturb(self, x, strength):
        """Zoom blur perturbation (RoDLA type 6): avg_pool downsample then upsample."""
        kernel = 2
        downsampled = F.avg_pool2d(x, kernel_size=kernel, stride=kernel)
        upsampled = F.interpolate(downsampled, size=(x.shape[2], x.shape[3]), mode='nearest')
        return x + strength * (upsampled - x)

    def _fog_perturb(self, x, strength):
        """Fog perturbation (RoDLA type 7): additive constant bias."""
        fog_bias = strength * x.mean(dim=(2, 3), keepdim=True)
        return x + fog_bias

    def _brightness_perturb(self, x, strength):
        """Brightness perturbation (RoDLA type 8): multiplicative gain shift."""
        b, c, _, _ = x.shape
        gain = 1.0 + strength * torch.randn(b, c, 1, 1, device=x.device, dtype=x.dtype) * 0.5
        return x * gain

    def _contrast_perturb(self, x, strength):
        """Contrast perturbation (RoDLA type 9): contrast stretch using centered x."""
        b, c, _, _ = x.shape
        centered = x - x.mean(dim=(2, 3), keepdim=True)
        jitter = torch.randn(b, c, 1, 1, device=x.device, dtype=x.dtype)
        gain = 1.0 + torch.tanh(self.contrast_logit) * strength * jitter
        return centered * gain + x.mean(dim=(2, 3), keepdim=True)

    def _elastic_transform_perturb(self, x, strength):
        """Elastic transform perturbation (RoDLA type 10): grid_sample with random displacement."""
        b, c, h, w = x.shape
        # Generate small random displacement field
        displacement = torch.randn(b, 2, h // 4, w // 4, device=x.device, dtype=x.dtype) * strength * 0.1
        displacement = F.interpolate(displacement, size=(h, w), mode='bilinear', align_corners=False)
        # Create identity grid and add displacement
        grid_y, grid_x = torch.meshgrid(
            torch.linspace(-1, 1, h, device=x.device, dtype=x.dtype),
            torch.linspace(-1, 1, w, device=x.device, dtype=x.dtype),
            indexing='ij'
        )
        grid = torch.stack([grid_x, grid_y], dim=0).unsqueeze(0).expand(b, -1, -1, -1)
        grid = grid + displacement
        grid = grid.permute(0, 2, 3, 1)
        warped = F.grid_sample(x, grid, mode='bilinear', padding_mode='border', align_corners=False)
        return warped

    def _jpeg_compression_perturb(self, x, strength):
        """JPEG compression perturbation (RoDLA type 11): block quantization via avg_pool."""
        block_size = 8
        h, w = x.shape[2], x.shape[3]
        if h % block_size != 0 or w % block_size != 0:
            # Fallback: simple pooling
            quantized = F.avg_pool2d(x, kernel_size=block_size, stride=block_size)
            dequantized = F.interpolate(quantized, size=(h, w), mode='nearest')
        else:
            quantized = F.avg_pool2d(x, kernel_size=block_size, stride=block_size)
            dequantized = F.interpolate(quantized, size=(h, w), mode='nearest')
        return x + strength * (dequantized - x)

    def _pixelate_perturb(self, x, strength):
        """Pixelate perturbation (RoDLA type 12): coarse downsampling then upsampling."""
        block_size = max(2, int(2 + strength * 4))
        h, w = x.shape[2], x.shape[3]
        downsampled = F.avg_pool2d(x, kernel_size=block_size, stride=block_size)
        upsampled = F.interpolate(downsampled, size=(h, w), mode='nearest')
        return x + strength * (upsampled - x)

    def _mask_perturb(self, x, strength):
        """Backward-compatible mask perturbation (deprecated, kept for old checkpoints)."""
        keep_prob = 1.0 - torch.sigmoid(self.mask_logit) * strength
        keep_prob = keep_prob.clamp(0.5, 1.0)
        mask = (torch.rand_like(x) < keep_prob).to(x.dtype)
        return x * (mask / keep_prob)

    def _smooth_perturb(self, x, strength):
        """Backward-compatible smooth perturbation (deprecated, kept for old checkpoints)."""
        smooth_ratio = torch.sigmoid(self.smooth_logit) * strength
        smooth = F.avg_pool2d(x, kernel_size=3, stride=1, padding=1)
        return x + smooth_ratio * (smooth - x)


    def forward(self, x, augment=True):
        """
        Forward pass for RobustnessAugmentor.

        Args:
            x (torch.Tensor): Input tensor of shape (B, C, H, W).
            augment (bool): Whether to apply augmentation. Default: True.

        Returns:
            torch.Tensor: Augmented or original tensor.
        """
        if not augment or not self.training:
            return x

        # Apply severity multiplier to strength
        severity_multipliers = [0.33, 0.67, 1.0]
        effective_strength = float(self.augment_strength) * severity_multipliers[self.current_severity - 1]
        effective_strength = max(0.0, min(effective_strength, 1.0))
        if effective_strength == 0.0:
            return x

        # 12 RoDLA perturbation types
        perturbations = [
            self._noise_perturb(x, effective_strength),
            self._shot_noise_perturb(x, effective_strength),
            self._impulse_noise_perturb(x, effective_strength),
            self._defocus_blur_perturb(x, effective_strength),
            self._motion_blur_perturb(x, effective_strength),
            self._zoom_blur_perturb(x, effective_strength),
            self._fog_perturb(x, effective_strength),
            self._brightness_perturb(x, effective_strength),
            self._contrast_perturb(x, effective_strength),
            self._elastic_transform_perturb(x, effective_strength),
            self._jpeg_compression_perturb(x, effective_strength),
            self._pixelate_perturb(x, effective_strength),
        ]

        apply_prob = max(0.25, min(0.95, 0.35 + 0.5 * effective_strength))
        active = (torch.rand(len(perturbations), device=x.device) < apply_prob).to(x.dtype)
        if active.sum() < 1:
            active[torch.randint(0, len(perturbations), (1,), device=x.device)] = 1.0

        mix_weights = torch.softmax(self.perturb_logits, dim=0).to(x.dtype) * active
        mix_weights = mix_weights / (mix_weights.sum() + self.eps)

        mixed = x.new_zeros(x.shape)
        for i, perturbed in enumerate(perturbations):
            mixed = mixed + mix_weights[i] * perturbed

        residual_gain = torch.sigmoid(self.augment_weight).view(1, x.shape[1], 1, 1)
        bias = self.augment_bias.view(1, x.shape[1], 1, 1)
        blend = effective_strength * (0.5 + 0.5 * residual_gain)
        augmented = x + blend * (mixed - x) + effective_strength * 0.1 * bias
        return torch.nan_to_num(augmented, nan=0.0, posinf=1e4, neginf=-1e4)

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from fastapi.testclient import TestClient

ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from local_medication_api.app import app


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke test for health-app local medication API")
    parser.add_argument("--image", required=True, help="Path to a local image")
    args = parser.parse_args()

    image_path = Path(args.image).expanduser().resolve()
    if not image_path.exists():
        raise FileNotFoundError(image_path)

    client = TestClient(app)
    with image_path.open("rb") as handle:
        response = client.post(
            "/recognize/medications",
            files=[("files", (image_path.name, handle.read(), "image/jpeg"))],
            data={"scene": "medication_recognition"},
        )

    print("status:", response.status_code)
    print(response.json())


if __name__ == "__main__":
    main()

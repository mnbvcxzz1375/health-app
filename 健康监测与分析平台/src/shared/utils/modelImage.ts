const UNSUPPORTED_IMAGE_TYPES = new Set(['image/avif', 'image/heic', 'image/heif'])

function replaceExtension(name: string, extension: string) {
  return name.replace(/\.[^.]+$/, '') + extension
}

async function drawToJpegBlob(file: File): Promise<Blob | null> {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return null
  }

  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(file)
    try {
      const canvas = document.createElement('canvas')
      canvas.width = bitmap.width
      canvas.height = bitmap.height
      const context = canvas.getContext('2d')
      if (!context) return null
      context.drawImage(bitmap, 0, 0)
      return await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
    } finally {
      bitmap.close()
    }
  }

  return await new Promise<Blob | null>((resolve) => {
    const image = new Image()
    const objectUrl = URL.createObjectURL(file)

    image.onload = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth || image.width
        canvas.height = image.naturalHeight || image.height
        const context = canvas.getContext('2d')
        if (!context) {
          resolve(null)
          return
        }
        context.drawImage(image, 0, 0)
        canvas.toBlob(resolve, 'image/jpeg', 0.92)
      } finally {
        URL.revokeObjectURL(objectUrl)
      }
    }

    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      resolve(null)
    }

    image.src = objectUrl
  })
}

export function needsModelImageTranscode(file: File) {
  return UNSUPPORTED_IMAGE_TYPES.has(file.type)
}

export async function normalizeFilesForModel(files: File[]) {
  const normalized = await Promise.all(
    files.map(async (file) => {
      if (!needsModelImageTranscode(file)) {
        return file
      }

      try {
        const jpegBlob = await drawToJpegBlob(file)
        if (!jpegBlob) return file

        return new File([jpegBlob], replaceExtension(file.name, '.jpg'), {
          type: 'image/jpeg',
          lastModified: file.lastModified,
        })
      } catch {
        return file
      }
    }),
  )

  return normalized
}

// Загрузка файлов в Cloudinary из браузера (тот же неподписанный пресет, что и в приложении).
export const CLOUD_NAME = process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME || "dcwp4nm3e";
export const UPLOAD_PRESET = process.env.NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET || "ProfilePIC";

export type UploadKind = "image" | "video" | "audio";

/**
 * Загружает файл и возвращает https-ссылку. Прогресс идёт через XHR, потому что fetch его не отдаёт.
 * Аудио Cloudinary хранит как «video». Большие видео сервер обрабатывает долго, поэтому таймаута нет.
 */
export function uploadToCloudinary(
  file: Blob,
  kind: UploadKind,
  onProgress?: (fraction: number) => void,
  fileName = "upload"
): Promise<string> {
  return new Promise((resolve, reject) => {
    const resource = kind === "image" ? "image" : "video";
    const form = new FormData();
    form.append("file", file, fileName);
    form.append("upload_preset", UPLOAD_PRESET);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `https://api.cloudinary.com/v1_1/${CLOUD_NAME}/${resource}/upload`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
    };
    xhr.onerror = () => reject(new Error("Соединение оборвалось. Проверьте интернет и попробуйте ещё раз"));
    xhr.onload = () => {
      try {
        const data = JSON.parse(xhr.responseText || "{}");
        if (xhr.status >= 200 && xhr.status < 300 && data.secure_url) {
          resolve(data.secure_url as string);
        } else {
          const reason: string | undefined = data?.error?.message;
          if (reason && /size|large/i.test(reason)) {
            reject(new Error("Файл слишком большой для загрузки. Сделайте видео короче или с меньшим размером"));
          } else {
            reject(new Error(reason ? `Не удалось загрузить: ${reason}` : "Не удалось загрузить файл"));
          }
        }
      } catch {
        reject(new Error("Не удалось загрузить файл"));
      }
    };
    xhr.send(form);
  });
}

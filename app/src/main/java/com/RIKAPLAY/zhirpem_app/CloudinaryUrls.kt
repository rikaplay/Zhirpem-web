package com.RIKAPLAY.zhirpem_app

/**
 * Сжатие картинок через Cloudinary: вместо оригинала в 3-5 МБ телефон скачивает файл нужного размера
 * (в формате WebP/AVIF, где поддерживается). Это заметно ускоряет ленту и экономит трафик и память.
 * Ссылки не из Cloudinary и GIF возвращаются без изменений.
 */
object CloudinaryUrls {
    private fun canTransform(url: String): Boolean =
        url.contains("res.cloudinary.com") &&
            url.contains("/upload/") &&
            !url.substringBefore('?').endsWith(".gif", ignoreCase = true) &&
            // если в ссылке уже есть преобразования, второй раз не добавляем
            !Regex("/upload/[a-z]{1,3}_[^/]*/").containsMatchIn(url)

    /** Картинка поста: ширина до [width] пикселей. */
    fun feedImage(url: String?, width: Int = 900): String? {
        if (url.isNullOrBlank() || !canTransform(url)) return url
        return url.replace("/upload/", "/upload/w_$width,c_limit,q_auto,f_auto/")
    }

    /** Аватарка: квадрат [size] x [size]. */
    fun avatar(url: String?, size: Int = 160): String? {
        if (url.isNullOrBlank() || !canTransform(url)) return url
        return url.replace("/upload/", "/upload/w_$size,h_$size,c_fill,q_auto,f_auto/")
    }
}

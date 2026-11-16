/** @type {import('next').NextConfig} */
const nextConfig = {
    // Сборка для Vercel: обычный режим Next.js (раньше стоял статический экспорт под Netlify)
    // Проект собирался без проверки типов TypeScript. Включите проверку, когда соберёте сайт локально
    // командой `npm run build` и исправите замечания.
    typescript: {
        ignoreBuildErrors: true,
    },
    eslint: {
        // ESLint в проекте не настроен, не даём ему блокировать деплой
        ignoreDuringBuilds: true,
    },
    images: {
        unoptimized: true,
        remotePatterns: [
            {
                protocol: 'https',
                hostname: 'res.cloudinary.com',
                pathname: '/**',
            },
        ],
    },
};

export default nextConfig;

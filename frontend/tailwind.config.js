/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#ecfff8',
          100: '#ccffed',
          200: '#9af9dc',
          300: '#5eecc5',
          400: '#2dd4aa',
          500: '#10b981',
          600: '#059669',
          700: '#087a5d',
          800: '#075f49',
          900: '#064e3b'
        },
        navy: {
          50: '#f2f6f8',
          100: '#dfe9ee',
          500: '#2b4b5c',
          700: '#17313d',
          800: '#102530',
          900: '#071c23'
        },
        grape: {
          50: '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#4c1d95'
        },
        coral: {
          50: '#fff1f2',
          100: '#ffe4e6',
          200: '#fecdd3',
          300: '#fda4af',
          400: '#fb7185',
          500: '#f43f5e',
          600: '#e11d48',
          700: '#be123c'
        },
        sun: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b',
          600: '#d97706',
          700: '#b45309'
        },
        canvas: '#f7f7ff',
        mint: '#e9fff7',
        peach: '#fff3ea'
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        display: ['Space Grotesk', 'ui-sans-serif', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 2px 4px rgba(15, 23, 42, 0.04), 0 18px 45px -28px rgba(49, 46, 129, 0.32)',
        soft: '0 20px 60px -28px rgba(49, 46, 129, 0.36)',
        glow: '0 14px 35px -16px rgba(5, 150, 105, 0.6)'
      },
      borderRadius: {
        xl2: '1.25rem',
        '4xl': '2rem'
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' }
        },
        shimmer: {
          '0%': { backgroundPosition: '0% 50%' },
          '100%': { backgroundPosition: '100% 50%' }
        }
      },
      animation: {
        float: 'float 6s ease-in-out infinite',
        shimmer: 'shimmer 8s ease infinite'
      }
    }
  },
  plugins: []
};

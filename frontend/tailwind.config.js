/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#ecfdf6',
          100: '#d1fae7',
          200: '#a7f3d3',
          300: '#6ee7b7',
          400: '#34d3a0',
          500: '#0fa97f',
          600: '#0b8b68',
          700: '#0c6f55',
          800: '#0d5946',
          900: '#0b4638'
        },
        navy: {
          50: '#f2f6f8',
          100: '#dfe9ee',
          500: '#2b4b5c',
          700: '#17313d',
          800: '#102530',
          900: '#0a1a22'
        },
        canvas: '#f6faf8',
        mint: '#e4f7ef'
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif']
      },
      boxShadow: {
        card: '0 1px 2px rgba(10, 26, 34, 0.06), 0 8px 24px -12px rgba(10, 26, 34, 0.18)',
        soft: '0 8px 30px -18px rgba(10, 26, 34, 0.35)'
      },
      borderRadius: {
        xl2: '1.25rem'
      }
    }
  },
  plugins: []
};

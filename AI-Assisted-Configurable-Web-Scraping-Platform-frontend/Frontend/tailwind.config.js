/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        elyadata: {
          blue: '#05BAEE',
          pink: '#D6007F',
          'dark-blue': '#040077',
          grey: '#FAFAFA',
          black: '#000000',
          white: '#FFFFFF',
          'typography-grey': '#535353',
        }
      },
      fontFamily: {
        sans: ['"Noto Sans"', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

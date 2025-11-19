export default {
  content: ["./src/**/*.html", "./src/js/**/*.js"],
  theme: {
    screens: {
      phone: "360px",
      tablet: "640px",
      desktop: "1024px"
    },
    extend: {
      colors: {
        brand: "#1e3a8a",
        brandAccent: "#2563eb"
      }
    }
  },
  plugins: []
};


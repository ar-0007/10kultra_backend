/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        // 10K Ultra — onyx surfaces with liquid-gold accents, sampled from the logo.
        gold: {
          bg: "rgb(var(--c-bg) / <alpha-value>)",
          surface: "rgb(var(--c-surface) / <alpha-value>)",
          card: "rgb(var(--c-card) / <alpha-value>)",
          card2: "rgb(var(--c-card2) / <alpha-value>)",
          border: "rgb(var(--c-border) / <alpha-value>)",
          primary: "rgb(var(--c-primary) / <alpha-value>)",
          primaryDark: "rgb(var(--c-primary-dark) / <alpha-value>)",
          ivory: "rgb(var(--c-text) / <alpha-value>)",
          muted: "rgb(var(--c-muted) / <alpha-value>)",
        },
        ok: "rgb(var(--c-ok) / <alpha-value>)",
        warn: "rgb(var(--c-warn) / <alpha-value>)",
        danger: "rgb(var(--c-danger) / <alpha-value>)",
      },
      backgroundImage: {
        // The brand gradient — used on primary buttons and the logo glow.
        "gold-sheen":
          "linear-gradient(135deg, rgb(var(--c-primary)) 0%, rgb(var(--c-primary-light)) 45%, rgb(var(--c-primary-dark)) 100%)",
      },
      boxShadow: {
        glow: "0 0 0 1px rgb(var(--c-primary) / 0.12), 0 10px 34px rgb(var(--c-shadow) / var(--shadow-strength))",
        gold: "0 0 0 1px rgb(var(--c-primary) / 0.35), 0 8px 26px rgb(var(--c-primary) / 0.18)",
      },
      keyframes: {
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(8px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-360px 0" },
          "100%": { backgroundPosition: "360px 0" },
        },
      },
      animation: {
        "fade-up": "fade-up 320ms cubic-bezier(0.22, 1, 0.36, 1) both",
        shimmer: "shimmer 1.3s linear infinite",
      },
    },
  },
  plugins: [],
};

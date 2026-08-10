import { useEffect, useState } from "react";
import { Sun, Moon } from "lucide-react";

function getInitialTheme() {
  if (typeof document !== "undefined") {
    const attr = document.documentElement.getAttribute("data-theme");
    if (attr) return attr;
  }
  return localStorage.getItem("theme") || "dark";
}

export default function ThemeToggle({ showLabel = false, className = "" }) {
  const [theme, setTheme] = useState(getInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("theme", theme);
  }, [theme]);

  const isDark = theme === "dark";
  const toggle = () => setTheme(isDark ? "light" : "dark");

  return (
    <button
      onClick={toggle}
      className={`btn-ghost ${showLabel ? "w-full justify-start" : "h-9 w-9 p-0"} ${className}`}
      aria-label="Toggle dark / light theme"
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
    >
      {isDark ? <Sun size={16} /> : <Moon size={16} />}
      {showLabel && <span>{isDark ? "Light mode" : "Dark mode"}</span>}
    </button>
  );
}

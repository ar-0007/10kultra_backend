export default function StatCard({ label, value, icon: Icon, accent }) {
  // Falls back to the brand gold so a card without an explicit accent still looks intentional.
  const tone = accent ?? "#E7B53C";
  return (
    <div className="card card-hover flex items-center gap-3 p-4 sm:gap-4 sm:p-5">
      <div
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl sm:h-12 sm:w-12"
        style={{ background: tone + "1F", color: tone, boxShadow: `inset 0 0 0 1px ${tone}33` }}
      >
        <Icon size={20} />
      </div>
      <div className="min-w-0">
        <p className="truncate text-xl font-bold tracking-tight text-gold-ivory sm:text-2xl">{value}</p>
        <p className="truncate text-xs text-gold-muted sm:text-sm">{label}</p>
      </div>
    </div>
  );
}

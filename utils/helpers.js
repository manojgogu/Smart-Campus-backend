function generateIncidentId() {
  const d = new Date();
  const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  const rand = Math.random().toString(36).slice(2, 7).toUpperCase();
  return `CS-${ymd}-${rand}`;
}

function audit(action, actor, details = '') {
  console.log(`[AUDIT] ${new Date().toISOString()} ${action} by ${actor?.email || actor} ${details}`);
}

module.exports = { generateIncidentId, audit };

// Minimal view router for the prototype
document.querySelectorAll('[data-go]').forEach(btn => {
  btn.addEventListener('click', () => {
    const t = btn.dataset.go;
    document.querySelectorAll('.screen').forEach(s => s.hidden = s.dataset.screen !== t);
  });
});

// RAM slider live value
const range = document.querySelector('.ram-range');
const num = document.querySelector('.ram-value__num');
if (range && num) {
  range.addEventListener('input', () => {
    num.textContent = range.value;
  });
}

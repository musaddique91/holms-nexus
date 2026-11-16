try {
  var t = localStorage.getItem('nexus-theme') || 'dark';
  var cl = document.documentElement.classList;
  cl.remove('dark', 'theme-emerald', 'theme-ember', 'theme-tide');
  if (t === 'emerald') {
    cl.add('dark', 'theme-emerald');
  } else if (t === 'ember') {
    cl.add('dark', 'theme-ember');
  } else if (t === 'tide') {
    cl.add('dark', 'theme-tide');
  } else {
    var dark = t === 'dark' || (t === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) cl.add('dark');
  }
} catch (e) {}

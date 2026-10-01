(() => {
  if (window.__androidIdeDevtoolsLoaded) return;
  window.__androidIdeDevtoolsLoaded = true;
  if (window.eruda) {
    window.eruda.init({
      tool: ['console', 'elements', 'network', 'resources', 'sources', 'info', 'snippets'],
      useShadowDom: true,
      defaults: { displaySize: 50, transparency: 0.9 }
    });
  }
})();

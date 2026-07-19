<template>
  <div class="pg-bg-stars" aria-hidden="true"></div>
  <div class="pg-bg-blob" aria-hidden="true"></div>
  <router-view />
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body, #app {
  height: 100%;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

body::before, body::after, .pg-bg-blob {
  content: "";
  position: fixed;
  border-radius: 50%;
  filter: blur(var(--pg-blob-blur));
  z-index: 0;
  opacity: var(--pg-blob-opacity);
  pointer-events: none;
  transition: background 0.5s ease, opacity 0.5s ease;
}
body::before {
  width: 620px; height: 620px;
  background: var(--pg-bg-blob-1);
  top: -140px; left: -100px;
}
body::after {
  width: 520px; height: 520px;
  background: var(--pg-bg-blob-2);
  bottom: -160px; right: -80px;
}
.pg-bg-blob {
  width: 380px; height: 380px;
  background: var(--pg-bg-blob-3);
  top: 40%; left: 55%;
}
.pg-bg-stars {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  opacity: var(--pg-stars-opacity);
  transition: opacity 0.5s ease;
  background-image:
    radial-gradient(1px 1px at 15% 20%, rgba(255,255,255,0.5) 50%, transparent),
    radial-gradient(1px 1px at 40% 65%, rgba(255,255,255,0.4) 50%, transparent),
    radial-gradient(1px 1px at 70% 30%, rgba(255,255,255,0.5) 50%, transparent),
    radial-gradient(1px 1px at 85% 80%, rgba(255,255,255,0.3) 50%, transparent),
    radial-gradient(1px 1px at 25% 85%, rgba(255,255,255,0.4) 50%, transparent);
}
/* 保证主内容盖过背景 */
#app > *:not(.pg-bg-stars):not(.pg-bg-blob) {
  position: relative;
  z-index: 1;
}
</style>

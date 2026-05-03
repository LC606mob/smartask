declare module 'vue-markdown-shiki' {
  import type { Component, Plugin } from 'vue';

  export const VueMarkdownIt: Component;
  export const VueMarkdownItProvider: Component;

  const markdownPlugin: Plugin;
  export default markdownPlugin;
}

import path from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  resolve: {
    alias: {
      '@examples-lib': path.resolve(__dirname, '../lib'),
    },
  },
  plugins: [react(), tailwindcss()],
});

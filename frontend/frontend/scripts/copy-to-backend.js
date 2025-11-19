// Копирование собранных стилей и index.html в backend static
import { cpSync, mkdirSync } from 'fs';
import { join } from 'path';

const root = process.cwd();
const dist = join(root, 'dist');
const targetStatic = join(root, '..', 'src', 'main', 'resources', 'static');

try { mkdirSync(targetStatic, { recursive: true }); } catch {}
cpSync(join(dist, 'assets', 'styles.css'), join(targetStatic, 'styles.css')); // стиль
cpSync(join(root, 'src', 'index.html'), join(targetStatic, 'index.html')); // html
console.log('Copied assets to backend static');


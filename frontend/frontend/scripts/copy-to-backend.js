// Копирование собранных стилей и index.html в backend static
import { cpSync, mkdirSync, existsSync, statSync, copyFileSync, readdirSync } from 'fs';
import { join, resolve } from 'path';

const root = process.cwd();
const dist = join(root, 'dist');
const targetStatic = resolve(join(root, '..', 'src', 'main', 'resources', 'static'));

// CLI flags
const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const confirm = args.includes('--yes') || args.includes('-y');

// If build is running in frontend Docker image (or env variable set), skip
if (process.env.SKIP_COPY_FOR_BUILD) {
	console.log('SKIP_COPY_FOR_BUILD is set - skipping copy-to-backend');
	process.exit(0);
}

// Basic sanity checks: make sure we are copying into a Java resources static dir
function isJavaProjectRoot(backendRoot) {
	const possibleGradle = join(backendRoot, 'build.gradle');
	const possibleMaven = join(backendRoot, 'pom.xml');
	const possibleSrc = join(backendRoot, 'src', 'main', 'java');
	return existsSync(possibleGradle) || existsSync(possibleMaven) || existsSync(possibleSrc);
}

const backendRoot = resolve(join(root, '..'));
if (!isJavaProjectRoot(backendRoot)) {
	console.error('\nAborted: It looks like target backend project root is not detected.\n' +
		`Checked: ${backendRoot} for build.gradle/pom.xml/src/main/java\n` +
		'This prevents accidental copying into a non-java repo.');
	process.exit(1);
}

// Confirm action unless --yes provided
if (!confirm && !dryRun) {
	console.warn('\nSafety: No --yes flag provided. Re-run with --yes to allow copying, or use --dry-run to preview.');
	process.exit(0);
}

// Ensure target folder
try { mkdirSync(targetStatic, { recursive: true }); } catch {}

// Backup existing files (only if not dry run)
const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
const backupDir = targetStatic + `.backup.${timestamp}`;
if (!dryRun) {
	try { mkdirSync(backupDir, { recursive: true }); } catch {}
}

// Whitelist tasks: [srcPath, targetPath]
const tasks = [
	[join(dist, 'assets', 'styles.css'), join(targetStatic, 'styles.css')],
	[join(root, 'src', 'index.html'), join(targetStatic, 'index.html')],
	[join(root, 'src', 'login.html'), join(targetStatic, 'login.html')],
	[join(root, 'src', 'js'), join(targetStatic, 'js')],
	[join(root, 'src', 'assets'), join(targetStatic, 'assets')]
];

function backupAndCopy(src, dest) {
	if (!existsSync(src)) return false;
	const destExists = existsSync(dest);
	if (destExists && !dryRun) {
		const stat = statSync(dest);
		// If dest is a directory: copy individually into backup dir
		if (stat.isDirectory()) {
			const children = readdirSync(dest);
			children.forEach(child => {
				const childPath = join(dest, child);
				const targetBackup = join(backupDir, child);
				try { mkdirSync(join(backupDir), { recursive: true }); } catch {}
				cpSync(childPath, targetBackup, { recursive: true, force: true });
			});
		} else {
			try { mkdirSync(backupDir, { recursive: true }); } catch {}
			copyFileSync(dest, join(backupDir, pathBase(dest)));
		}
	}
	if (dryRun) {
		console.log(`[DRY RUN] Copy: ${src} -> ${dest}`);
		return true;
	}
	try {
		if (existsSync(src) && statSync(src).isDirectory()) {
			cpSync(src, dest, { recursive: true, force: true });
		} else {
			cpSync(src, dest, { force: true });
		}
		console.log(`Copied: ${src} -> ${dest}`);
		return true;
	} catch (e) {
		console.error(`Failed to copy ${src} -> ${dest}:`, e.message);
		return false;
	}
}

function pathBase(p) {
	return p.split(/[\\/]/).pop();
}

// Execute tasks
let ok = false;
for (const [src, dest] of tasks) {
	ok = backupAndCopy(src, dest) || ok;
}

if (!ok) console.warn('No files were copied (no files found or dry-run).');
else console.log('Copy complete. Backup stored at:', dryRun ? '(not created in dry-run)' : backupDir);


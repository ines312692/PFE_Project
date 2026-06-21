# AI-Assisted Configurable Web Scraping Platform — Frontend (ScrapeFlow UI)

This repository contains the Angular-based frontend for the AI-Assisted Configurable Web Scraping Platform (aka ScrapeFlow UI).
It provides the user interface to configure scraping flows, trigger runs, and visualize results. Built with Angular 21 and Tailwind CSS, and prepared for SSR.

Last updated: 2026-02-17 11:53

## Tech Stack
- Angular 21
- TypeScript 5
- Tailwind CSS + PostCSS (autoprefixer)
- RxJS
- Optional SSR runtime (Angular SSR + Express)

## Prerequisites
- Node.js 20+ (LTS recommended)
- npm 10 (repo sets packageManager to npm@10.8.2)

Check versions:
```bash
node -v
npm -v
```

## Getting Started
1) Install dependencies:
```bash
npm install
```

2) Start the development server:
```bash
npm start
# or
ng serve
```
Then open http://localhost:4200/ in your browser. The app reloads on file changes.

## Available Scripts
- Development
  - npm start → ng serve
  - npm run watch → ng build --watch --configuration development
- Build
  - npm run build → ng build (artifacts under dist/)
- Test
  - npm test → ng test
- SSR (optional, after building with SSR configuration)
  - npm run serve:ssr:frontend → node dist/frontend/server/server.mjs

Note: The SSR serve script expects the SSR build output. If you enable SSR for this project, run an SSR build first (per Angular SSR docs) so the server bundle exists at the path above.

## Project Structure (high level)
- src/ — application source
  - app/ — Angular app modules, features, and layout
  - assets/ — static assets (if used)
  - styles.* — global styles (Tailwind included via PostCSS config)
- public/ — static public files served as-is
- angular.json — Angular workspace configuration
- tailwind.config.js, postcss.config.js — styling pipeline configuration

## Styling
Tailwind CSS is configured via postcss.config.js and tailwind.config.js.
Use utility classes in templates and component styles. Prefer component-scoped styles for feature-specific styling.

## Development Tips
- Use Angular CLI generators to scaffold components, services, etc. Example:
```bash
ng generate component features/example/example-widget
```
- Keep feature code under src/app/features/<feature-name> for cohesion.

## Building for Production
```bash
npm run build
```
Outputs optimized production assets to dist/.

## Troubleshooting
- Port already in use: change the dev server port
```bash
ng serve --port 4300
```
- Dependency issues: remove lockfile and node_modules, then reinstall
```bash
rm -rf node_modules package-lock.json
npm install
```
- Node version mismatch: ensure Node 20+ per prerequisites.

## Contributing
- Follow Angular style guide and conventional commits where possible.
- Run the app locally and ensure no errors before submitting PRs.

## License
This project is licensed under the terms specified by the repository owner. If a LICENSE file is missing, please consult the project maintainer.

## References
- Angular CLI: https://angular.dev/tools/cli
- Angular SSR: https://angular.dev/guide/ssr
- Tailwind CSS: https://tailwindcss.com/

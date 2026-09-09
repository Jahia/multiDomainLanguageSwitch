import {defineConfig} from 'cypress';
import fs from 'fs';

// @jahia/cypress's env plugin OVERWRITES config.baseUrl in setupNodeEvents: with
// process.env.JAHIA_URL when it is set, and with http://localhost:8080 otherwise.
// A baseUrl declared below — or passed as --config baseUrl=... — is therefore
// discarded. So the test bed is driven through process.env instead, set here
// before the plugin runs. The plugin also takes SUPER_USER_PASSWORD from
// process.env in that same branch, so all three must be set together or the
// password comes out undefined.
process.env.JAHIA_URL = process.env.JAHIA_URL || `http://www.localtest.me:${process.env.LSM_PORT || '8080'}`;
process.env.JAHIA_PROCESSING_URL = process.env.JAHIA_PROCESSING_URL || process.env.JAHIA_URL;
process.env.SUPER_USER_PASSWORD = process.env.SUPER_USER_PASSWORD || 'root1234';

// The domain network under test. All four hostnames reach the same Jahia: through
// docker network aliases in CI (see docker-compose.yml), and through the public
// localtest.me wildcard (*.localtest.me -> 127.0.0.1) on a developer machine.
// The port is taken from JAHIA_URL so the whole network follows it.
const PORT = new URL(process.env.JAHIA_URL).port || '80';
const host = (name: string) => `http://${name}.localtest.me:${PORT}`;

export default defineConfig({
    reporter: 'cypress-multi-reporters',
    reporterOptions: {
        configFile: 'reporter-config.json'
    },
    screenshotsFolder: './results/screenshots',
    video: true,
    videosFolder: './results/videos',
    viewportWidth: 1366,
    viewportHeight: 768,
    watchForFileChanges: false,
    e2e: {
        setupNodeEvents(on, config) {
            on(
                'after:spec',
                (spec: Cypress.Spec, results: CypressCommandLine.RunResult) => {
                    if (results && results.video) {
                        const failures = results.tests.some(test =>
                            test.attempts.some(attempt => attempt.state === 'failed')
                        );
                        if (!failures) {
                            fs.unlinkSync(results.video);
                        }
                    }
                }
            );
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            return require('./cypress/plugins/index.js')(on, config);
        },
        excludeSpecPattern: '*.ignore.ts',
        // Informational: the plugin above replaces this with JAHIA_URL, which is
        // the same value. Kept so the intent is readable without running anything.
        baseUrl: process.env.JAHIA_URL
    },
    env: {
        HOST_WWW: host('www'),
        HOST_DE: host('de'),
        HOST_IT: host('it'),
        HOST_CH: host('ch')
    }
});

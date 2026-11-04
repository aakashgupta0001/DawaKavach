/* DawaCheck - config.js
 *
 * One place to point the frontend at the Spring Boot backend.
 *
 * USE_MOCK = true   -> every call is served by js/mock-api.js in the browser,
 *                      so the UI is fully demoable before the backend exists.
 * USE_MOCK = false  -> every call goes to API_BASE using fetch(), hitting the
 *                      REST endpoints listed in section 13 of the documentation.
 *
 * Nothing else in the app reads these values directly; js/api.js does.
 */
window.DAWA_CONFIG = {
  USE_MOCK: false,

  // Spring Boot origin. Empty string means "same origin as this page",
  // which is what happens once the pages are served from src/main/resources/static.
  API_BASE: '',

  // Simulated latency for mock calls, in ms. Keeps loading states honest.
  MOCK_DELAY: 320,

  // Default UI language: 'en' or 'hi'.
  DEFAULT_LANG: 'en',

  // Upload limits mirrored from the backend so we can fail fast in the browser.
  UPLOAD: {
    MAX_BYTES: 8 * 1024 * 1024,
    ACCEPTED: ['image/jpeg', 'image/png', 'image/webp']
  },

  // Fuzzy-match confidence below which the user is asked to type the name
  // instead of picking a suggestion (section 10, brand matching).
  MATCH_CONFIDENCE_FLOOR: 0.62
};

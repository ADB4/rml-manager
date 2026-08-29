# Coding Style Guide

Reference document for AI coding sessions. Derived from the rogerlib codebase (Python/Flask + React/TypeScript), idealized and tightened. The **Core Principles** and **Naming** sections are stack-agnostic and apply to any language (including Java/Spring Boot); stack-specific sections follow.

---

## Core Principles (all stacks)

1. **Explicit over clever.** Break multi-step transformations into named intermediate variables that document the pipeline. A hash derivation reads as `input → hash → digest → identifier`, each step on its own line with a descriptive name. Never collapse a readable sequence into a nested one-liner.

2. **Thin entry points, fat helpers.** Route handlers, controllers, and event handlers do three things only: receive input, delegate to a named helper, assemble and return a response. All real logic lives in dedicated utility/service functions.

3. **Assemble responses as explicit structures.** Build response payloads as a named `context` object/dict with every field spelled out — no spreading opaque objects straight through. Include request metadata (`timestamp`, `url`) on API responses.

4. **Fail fast at boundaries.** Validate assumptions at access points and throw immediately with a specific message (e.g., context accessors that throw `'Model context not found.'` when used outside a provider). Never let an undefined value travel deeper into the call stack.

5. **Comments explain *why* and *shape*, not *what*.** Prefer comments that document intent, tradeoffs, or data transformations — including worked examples:
   ```
   # /static/assets/category/subcategory/itemcode translates to ->
   # /<hash("static")>/<hash("assets")>/<hash(category)>/...
   ```
   Honest engineering notes are welcome (`# security by obfuscation / security by inconvenience`, `# postgresql database not currently used`).

6. **Layered configuration.** A common base config plus environment overlays (`common` → `dev`/`prod` → env-var overrides), selected by environment variable. Never hardcode environment-specific values in application logic.

7. **Small files, single responsibility.** One component, hook, or cohesive helper group per file. When a file grows past ~250 lines, look for a seam to split.

8. **Delete dead code; don't comment it out.** (Tightened rule — the repo has commented-out blocks; new code must not.)

---

## Naming Conventions

Verb-prefixed function names that state what they do, across all stacks:

| Role | Python/Flask | TypeScript/React | Java/Spring |
|---|---|---|---|
| Retrieve data | `fetch_presigned_url` | `fetchCategories` | `fetchPresignedUrl` |
| Transform data | `parse_item`, `hydrate` | `parseItem` | `parseItem`, `hydrate` |
| Route/page handler | `show_gallery`, `get_item` | — | `getItem` |
| UI event handler | — | `handleClick`, `handleFilterResults` | — |
| Boolean flip | — | `toggleVisibility`, `toggleDarkMode` | — |
| Accumulator/result var | `result`, `context` | `result`, `current` | `result`, `context` |

- **Types/interfaces:** PascalCase with a `Type` suffix — `ItemType`, `DetailViewType`, `ColorSchemeType`, `DictType`. Java equivalent: plain PascalCase DTOs/records (`ItemDto`), since the `Type` suffix reads oddly there.
- **Python:** `snake_case` functions and variables; module-level constants lowercase unless truly constant.
- **TypeScript:** `camelCase` variables/functions, PascalCase components and types.
- **Files:** frontend components are camelCase with a role suffix — `galleryComponent.tsx`, `filterComponent.tsx`; hooks are `useDevice.tsx`; tests are colocated as `useDevice.test.tsx`. Backend modules are short snake_case nouns: `util.py`, `errors.py`, `index.py`.
- **API routes:** versioned, lowercase, trailing slash — `/api/v1/items/<itemcode>/`, `/api/v1/categories/all/`.

---

## Architecture Conventions

### Project layout
Separate the page-serving layer from the JSON API layer, and both from logic:

```
backend-package/
├── __init__.py        # app wiring: CORS, rate limiter, config overlay
├── config_common.py   # shared config
├── config_dev.py      # dev overlay
├── config_prod.py     # prod overlay
├── views/             # HTML page routes + error handlers
│   ├── index.py
│   ├── errors.py
│   └── util.py        # all business/helper logic
└── api/               # JSON endpoints only
    └── <domain>.py

frontend/src/
├── component/         # one component per file
├── context/           # typed contexts + accessor hooks
├── hooks/             # custom hooks, tests colocated
└── style/             # shared style objects
```

Java/Spring translation: `controller/` (thin, like `api/`), `service/` (like `views/util.py`), `config/` with profile-based `application-{profile}.yml` (like the config overlay pattern), `dto/` for explicit response shapes.

### API design
- Version prefix (`/api/v1/`) on every JSON endpoint.
- A `/health` endpoint returning status + version.
- Every list/detail response is an explicitly assembled object; include `timestamp` and `url` metadata.
- Rate limiting and CORS configured centrally at app initialization, with explicit origin allowlists — never `*` in production.

### External services
- Wrap third-party calls (S3, DBs) in dedicated `fetch_*` helpers; the route never touches the client SDK directly.
- Client construction, credentials, and region come from config — never inline.
- Catch the SDK's specific exception class; re-raise or translate, never swallow.
- Sensitive object paths use hashed/obfuscated keys rather than guessable names, with the derivation documented in a comment.

---

## React / TypeScript

- **Functional components only**, default-exported, one per file. Hooks for all state and effects.
- **Strict typing everywhere** (tightened rule): every prop destructure gets an interface — never `function GalleryComponent({ outData })` with implicit `any`. Type callback props precisely: `outData: (selection: string[]) => void`. No `PropTypes` — TypeScript is the single source of truth. Use explicit generics on state: `useState<boolean>(false)`, `useState<ItemType[]>([])`.
- **`.tsx` only** — no mixed `.jsx` files (tightened rule).
- **Strict equality** `===`/`!==` always (tightened rule).
- **Context pattern (signature pattern — always follow):** every context is typed, initialized `undefined`, and paired with an accessor hook that throws:
  ```tsx
  export const ModelContext = createContext<ItemType | undefined>(undefined);
  export const useModelContext = () => {
      const context = useContext(ModelContext);
      if (context === undefined) {
          throw new Error('Model context not found.');
      }
      return context;
  };
  ```
  Components consume `useModelContext()`, never raw `useContext`.
- **Child → parent communication:** pass a callback prop named `outData` (or `out<Thing>` for multiple channels); child calls it with the new value after updating its own state.
- **Compound UI state** goes in a single typed object, updated wholesale:
  ```tsx
  interface DetailViewType { item: ItemType | null; toggle: boolean; }
  const [detailViewConfig, setDetailViewConfig] = useState<DetailViewType>({ item: null, toggle: false });
  ```
- **Animation sequencing:** stage state transitions with an async `delay(ms)` helper (set pre-state → await delay → set post-state) rather than CSS-only hacks when JS coordination is needed.
- **Data fetching in effects** uses the stale-request guard:
  ```tsx
  useEffect(() => {
      let ignoreStaleRequest = false;
      fetch(url, { credentials: "same-origin" })
          .then((response) => {
              if (!response.ok) throw Error(response.statusText);
              return response.json();
          })
          .then((data) => {
              if (!ignoreStaleRequest) { /* set state */ }
          })
          .catch((error) => console.error(error));
      return () => { ignoreStaleRequest = true; };
  }, []);
  ```
- **Styling:** CSS classes handle static layout; dynamic theming uses inline objects typed `React.CSSProperties` with ternaries keyed on `darkMode` / `compactView`. Shared style objects live in `src/style/`.
- **Conditional rendering** with `{condition && (...)}` blocks and fragments (`<>`), not nested ternaries in JSX.
- **Responsive/device logic** lives in a hook (`useDevice`) provided app-wide via context — components read `compactView`, they never sniff `navigator` themselves.
- **Formatting:** 4-space indentation, double quotes in TSX, semicolons always (tightened rule — apply uniformly).

---

## Python / Flask

- **Route handlers stay thin** — fetch via helper, build `context` dict, `return flask.jsonify(**context)`.
- **Namespaced flask calls:** `import flask` and call `flask.render_template(...)`, `flask.jsonify(...)`, `flask.request.path` — don't `from flask import render_template`.
- **String formatting:** f-strings for all new code (tightened from the repo's `str.format(...)` habit — same explicitness, modern form).
- **Logging over print** (tightened rule): use the `logging` module with appropriate levels; no bare `print()` debugging left in committed code.
- **Docstrings** on any function whose behavior isn't obvious from its name; one-line summary style (`"""Return item from itemcode"""`). Parameter-documenting docstrings for functions with non-trivial signatures.
- **Imports:** stdlib and third-party grouped at top, one per line, roughly alphabetized; `from`-imports after plain imports.
- **Error handling:** wrap external calls in `try/except <SpecificError>`; error handlers render a shared error template with a `context` dict.
- **Environment switching** via `FLASK_ENV` checked once at app init, overlaying config modules — logic elsewhere reads `app.config`, never `os.environ` directly (tightened rule).
- **Formatting:** 4-space indentation, PEP 8 baseline.

---

## Testing

Patterns from the vitest suites — translate directly to pytest / JUnit:

- **Organize suites into three tiers** with nested describe blocks (or test classes): `Unit Tests`, `Integration Tests`, `Edge Cases`.
- **Test names read as behavior specs:** `should return false for desktop user agents`, `should remove resize event listener on unmount`.
- **Mock infrastructure is built as reusable helpers** at the top of the file (`mockUserAgent`, `mockEventListener` returning `{ addEventListener, removeEventListener, triggerEvent }`), not inlined per-test.
- **Save and restore globals** in `beforeEach`/`afterEach`; always `vi.clearAllMocks()` (or equivalent) in teardown. Tests never leak state.
- **Edge cases are first-class:** undefined/null inputs, empty strings, case sensitivity, rapid repeated events, remounting — each gets its own test.
- **Table-driven where natural:** iterate arrays of fixtures (multiple user agents) inside one test rather than copy-pasting.
- Frontend: vitest + Testing Library (`renderHook`, `act`, `render`); assert on rendered output via `data-testid`, not implementation internals.

---

## Carrying Over to Java / Spring Boot

Direct translations of the patterns above:

- Thin `@RestController` → `Service` methods named `fetchX`/`parseX` → explicit DTO assembly (records preferred). The `context` dict becomes a named response record with `timestamp` and `url` fields.
- Config overlay pattern → Spring profiles (`application.yml` + `application-dev.yml` + `application-prod.yml`), selected by `SPRING_PROFILES_ACTIVE`; code reads `@ConfigurationProperties`, never `System.getenv` directly.
- Fail-fast accessor pattern → constructor injection with `Objects.requireNonNull`, and `Optional` returns resolved with `.orElseThrow(() -> new SpecificException("..."))` at the boundary — mirroring the context-hook throw.
- Specific exception handling → catch the SDK's exception class, translate via `@ControllerAdvice` handlers (the equivalent of `errors.py`).
- Strict typing everywhere → no raw types, no unchecked casts; explicit generics as in `useState<ItemType[]>`.
- Same naming discipline: verb-prefixed methods, explicit intermediate variables in multi-step transforms, versioned `/api/v1/...` routes, `/health` endpoint.

---

## Quick Directives Summary

When generating or editing code in this style:

- DO break transformations into named intermediate steps.
- DO keep route/controller/handler bodies to fetch → assemble → return.
- DO type everything explicitly; no implicit `any`, no untyped props, no raw types.
- DO use the throwing-accessor pattern for shared state (contexts, injected deps).
- DO include `timestamp`/`url` metadata in API responses and version routes under `/api/v1/`.
- DO write behavior-spec test names in Unit/Integration/Edge Case tiers with reusable mock helpers and clean teardown.
- DON'T leave `print`/`console.log` debugging, commented-out code, or dead imports.
- DON'T use loose equality, PropTypes, or mixed `.jsx`/`.tsx`.
- DON'T touch SDK clients or environment variables outside config/helper layers.

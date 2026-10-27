# app

Expo (React Native, TypeScript strict) mobile app for iOS and Android.

## Run locally

```bash
npm install
npx expo start
```

Uses a development build (expo-dev-client), not Expo Go, because HealthKit, Health Connect and Sign in with Apple are native modules. Build one with:

```bash
eas build --profile development --platform ios      # or android
```

## Checks

```bash
npx tsc --noEmit
npx eslint .
npm test
```

## API client

Generated from `../api/openapi.json`. Never hand-write request or response types.

```bash
npm run gen:api
```

## Offline

Every write goes to local SQLite and an outbox first, then syncs. The UI reads from SQLite, never directly from the network.

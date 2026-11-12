# FCM wake-up (so sleeping phones ring quickly)

Without FCM the app relies on its own background connection, which phone makers sometimes kill.
FCM is the one path Android reliably uses to wake a sleeping phone. Only a tiny alert message passes through Google.

1. Create a Firebase project (free Spark plan is enough). Add **two** Android apps: `com.homepager.family` and `com.homepager`.
2. Download `google-services.json` and place it at `android/app/google-services.json` (the build enables FCM automatically when this file exists).
   For GitHub Actions: add the file's full contents as repository secret `GOOGLE_SERVICES_JSON`.
3. Project settings > Service accounts > generate a private key. Save it as `server/service-account.json`.
4. In `server/.env` set `FCM_PROJECT_ID=<your project id>`; in `docker-compose.yml` uncomment the `service-account.json` volume line.
5. Restart the server. Phones register their token automatically when they connect.

Check: put a phone to sleep for 20 minutes, trigger an alert from another phone; it should ring within a few seconds.

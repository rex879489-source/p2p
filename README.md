# P2P Chat

A minimal Android app with **no server, no backend, no accounts**.

- On first launch, a random username is generated and stored locally on the device (e.g. `BraveOtter42`).
- To connect to someone: one device creates a **connection code** (text blob), sends it through *any* app you like (WhatsApp, SMS, email, in person) — the other device pastes it in, generates a **reply code**, sends that back. Once both codes are exchanged, the two phones connect **directly** peer-to-peer.
- Chat messages and voice audio both flow over that same direct WebRTC connection. Everything is encrypted automatically by WebRTC (DTLS-SRTP) — no custom crypto needed.
- The only outside touchpoint is Google's public STUN server, used only to discover each phone's public IP address for the handshake. It never sees messages, files, or identities.

## How to use
1. Open the app on both phones.
2. On phone A: tap **"Start a new connection"** → copy the generated code → send it to phone B (any app).
3. On phone B: tap **"Join with a code"** → paste the code → tap **"Generate reply code"** → copy it → send it back to phone A.
4. On phone A: paste the reply code → tap **Connect**.
5. Chat and talk — fully peer-to-peer from here on.

## Building
This repo includes a GitHub Actions workflow (`.github/workflows/build.yml`) that automatically builds a debug APK on every push to `main`/`master`. After pushing:
1. Go to the **Actions** tab on GitHub.
2. Open the latest **Build APK** run.
3. Download the `app-debug-apk` artifact — that's your installable APK.

To build locally instead: open the project in Android Studio (Koala or newer) and run it, or use `./gradlew assembleDebug` (requires the Gradle wrapper — run `gradle wrapper` once if `gradlew` isn't present).

## Notes / things worth knowing
- **NAT limitation**: direct P2P connection works for most home/cellular networks. Some strict "symmetric NAT" networks (common on some corporate/public WiFi) will fail to connect since there's no TURN relay configured — that's a deliberate tradeoff to keep this fully serverless. If you hit that a lot, you can add a TURN server URL to `WebRtcClient.kt`'s `iceServers` list.
- **Reconnecting**: this simple version requires a fresh code exchange each session. Persisting a peer's info for automatic reconnect would be a good next step.
- **Group chats / calls** aren't supported — this is strictly 1:1.

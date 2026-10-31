# Discord "Playing Plegumo Client"

1. Open https://discord.com/developers/applications and click **New Application**. Name it **Plegumo Client**; that name is what appears after "Playing".
2. On the **General Information** page, copy the **Application ID**.
3. Go to **Rich Presence → Art Assets**, upload the Plegumo logo (PNG, 512x512 or larger) and name it `logo`. Save. It can take a few minutes to appear.
4. Put the ID in `.minecraft/config/plegumo-discord.json`:
   ```json
   { "clientId": "YOUR_ID", "largeImage": "logo", "buttonLabel": "", "buttonUrl": "" }
   ```
   You can also set `buttonLabel` and `buttonUrl` (https) to add a button such as "Download Plegumo".
5. With Discord open on the computer, start the game. In Settings, the "Discord Status" mod shows the connection state and has a Reconnect button.

Discord → Settings → Activity Privacy → "Share my activity" must be on.

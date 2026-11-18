# Tailscale Setup

[Tailscale](https://tailscale.com/) creates a private network between your own devices. Your phone can reach SillyTavern on your computer from anywhere — home Wi-Fi, mobile data, a café — without opening anything to the internet. The free personal plan is enough.

All addresses below are examples. Use your own.

## 1. Install Tailscale on Both Devices

1. **Computer:** download Tailscale from [tailscale.com/download](https://tailscale.com/download), install it, and sign in
2. **Phone:** install Tailscale from the Play Store and sign in with the **same account**
3. On the phone, turn Tailscale on. Android shows a VPN key icon in the status bar while it's connected

Open the Tailscale app on either device: both should be listed. Note your computer's Tailscale IP — it starts with `100.` (e.g. `100.64.0.2`).

## 2. Let SillyTavern Accept Connections

By default SillyTavern only answers on the computer itself. Stop SillyTavern and open `config.yaml` in the SillyTavern folder. Then pick **one** of the two setups below, and set up the whitelist after it.

The examples use `100.64.0.2` for the computer's Tailscale address and `100.64.0.3` for the phone's. Use your own, shown in the Tailscale app.

### Option A: Tailscale Only (More Locked Down)

Only your Tailscale devices can reach SillyTavern, wherever they are. Other devices on the same Wi-Fi as your computer can't connect at all.

```yaml
listen: true
listenAddress:
  ipv4: 100.64.0.2       # your computer's Tailscale address
browserLaunch:
  hostname: 100.64.0.2   # so the browser SillyTavern opens at startup still works
```

Things to know:

- `localhost` stops working, even on the computer. Open SillyTavern at `http://100.64.0.2:8000` there too, which is why the computer also goes on the whitelist below
- Tailscale has to be running before SillyTavern starts. If it isn't, that address doesn't exist yet and SillyTavern can't start
- If you set up HTTPS later with `tailscale serve`, switch to `listen: false` (see below), because `tailscale serve` connects to SillyTavern through `localhost`

### Option B: Tailscale and Local Network (Simpler)

Your Tailscale devices can reach SillyTavern, and so can any device on the same Wi-Fi as your computer (or any other network it joins). The whitelist or SillyTavern's login is what keeps those out. `localhost` keeps working on the computer, and the startup browser needs no change.

```yaml
listen: true
listenAddress:
  ipv4: 0.0.0.0          # every network (the default)
```

Things to know:

- Don't turn off both the whitelist and the login
- On a laptop that joins other networks (cafés, work), devices on those networks can try to connect too

### The Whitelist

With `whitelistMode: true`, SillyTavern only answers addresses on the list. With Option A, that includes **the computer itself**, since it connects through its Tailscale address too. Pick one:

- **List each device:**
  ```yaml
  whitelistMode: true
  whitelist:
    - ::1
    - 127.0.0.1
    - 100.64.0.2   # your computer's Tailscale address
    - 100.64.0.3   # your phone's Tailscale address
  ```
  Add a line for each new device you want to connect later.
- **Allow every device on your tailnet.** One line covers your computer, your phone, and any device you add later:
  ```yaml
  whitelistMode: true
  whitelist:
    - ::1
    - 127.0.0.1
    - 100.64.0.0/10   # every Tailscale address
  ```
- **Or** turn the whitelist off (`whitelistMode: false`) and turn on SillyTavern's login instead (`basicAuthMode: true` with a username and password). The app supports this login

Start SillyTavern again. If Windows asks whether Node.js may use the network, allow it on private networks.

## 3. Test from the Phone

1. With Tailscale on, open your phone's browser and go to `http://100.64.0.2:8000` (your computer's Tailscale IP and SillyTavern's port, 8000 by default)
2. If SillyTavern loads, the network is working
3. Enter the same address on TravelingTavern's **Connect** screen, tap **Test**, then **Save**

## Optional: HTTPS with `tailscale serve`

Plain `http://` over Tailscale is already encrypted — Tailscale encrypts everything between your devices. HTTPS is still worth setting up later, because Android only allows microphone access in the app's web view on secure (HTTPS) pages, which voice features need.

1. In the [Tailscale admin console](https://login.tailscale.com/admin/dns), turn on **MagicDNS** and **HTTPS Certificates**
2. On the computer, run:
   ```
   tailscale serve --bg 8000
   ```
3. Run `tailscale serve status` to see your address, something like `https://desktop.tail1234.ts.net`
4. In TravelingTavern, change the server to that address (no port)
5. Set `listen: false` in SillyTavern's `config.yaml` (required with Option A, optional with Option B). `tailscale serve` reaches SillyTavern through `localhost`, so SillyTavern then only needs to answer on the computer itself, and the HTTPS address becomes the only way in from other devices

## Troubleshooting

| Problem | Check |
|---|---|
| App says it can't reach the server | Tailscale is on in the phone (VPN key icon), the computer is awake, SillyTavern is running |
| SillyTavern won't start after choosing Option A | Tailscale isn't running on the computer yet, or `listenAddress` doesn't match its current Tailscale address. Start Tailscale first, then SillyTavern |
| Works at home, not away | You're using the computer's home address (`192.168…`) instead of its Tailscale address (`100.…`) |
| Browser shows "Forbidden" or a whitelist error | That device's Tailscale address isn't in SillyTavern's `whitelist` — on the computer too, if you open SillyTavern through its Tailscale address. See step 2 |
| Another VPN app is on | Android allows one VPN at a time. Turn the other one off |
| Only TravelingTavern can't connect | Tailscale's **App exclusion** setting on the phone might be skipping the app. Remove it from the excluded list |
| Computer goes to sleep | Set Windows to never sleep while plugged in, or the server disappears when it does |

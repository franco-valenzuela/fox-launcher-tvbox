# Security

## Report a problem in private

If you find a security problem in the launcher, the prompt or the scripts, please **do not open a public issue**.

Use GitHub's private report instead:

1. Open the **Security** tab of this repository.
2. Press **Report a vulnerability**.
3. Say what you found and how to see it happen.

You will get an answer as soon as possible. A fix goes into the main branch with credit to you, unless you prefer no credit.

Never include your IP address, your Wi-Fi name or your accounts in a report.

## What the launcher can and cannot do

Fox Launcher asks for three permissions. You can read them in [`launcher/AndroidManifest.xml`](launcher/AndroidManifest.xml).

| Permission | Why |
|---|---|
| `ACCESS_WIFI_STATE` | To show the name of the Wi-Fi network at the top of the screen |
| `KILL_BACKGROUND_PROCESSES` | For the "Free memory" button |
| `PACKAGE_USAGE_STATS` | To show when each app was last opened and for how long. It only works after the owner allows it |

It does **not** have the Internet permission. It cannot send or receive anything over the network. It has no background service and it does not start by itself in the background.

## Check that an APK is the official one

See [`docs/VERIFY.md`](docs/VERIFY.md).

## Keep your box safe

- Turn **USB debugging** and **network debugging** off when you finish. A box with that door open can be controlled by any device on the same network.
- Many cheap boxes come with unknown software from the factory. Do not use your main accounts on them.
- Turn on two-step sign-in for the accounts you use on the box.

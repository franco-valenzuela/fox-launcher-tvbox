# Contributing

Thank you for helping. You can write in Spanish or in English.

## Where does it go?

| You have | Put it here |
|---|---|
| Your own version, a fork, an improvement you want to show | [Discussions, "Show and tell"](https://github.com/franco-valenzuela/fox-launcher-tvbox/discussions/categories/show-and-tell) |
| An idea | [Discussions, "Ideas"](https://github.com/franco-valenzuela/fox-launcher-tvbox/discussions/categories/ideas) |
| A question | [Discussions, "Q&A"](https://github.com/franco-valenzuela/fox-launcher-tvbox/discussions/categories/q-a) |
| Something broken, or a concrete proposal | [Issues](https://github.com/franco-valenzuela/fox-launcher-tvbox/issues/new/choose) |
| A security problem | Private report. See [SECURITY.md](SECURITY.md) |
| A fix or a translation correction | A pull request |

## Tell us about your box

The project was tested on one box. Reports from other boxes are the most useful thing you can give. Please say:

- Brand and model of the box, and its processor if you know it.
- The Android version **and the SDK number** (the assistant shows both).
- Which fixes you applied and what changed.

## Protect your privacy

Before you post anything, take out:

- IP addresses and MAC addresses.
- The name of your Wi-Fi.
- Your computer's user name and file paths.
- E-mail addresses and account names.

`tools/privacy-scan.ps1` looks for these in a folder. The same check runs on every pull request.

## Pull requests

- One topic per pull request. Small is good.
- Say which box you tested on. A change to the launcher needs a screenshot from a real box.
- The launcher stays small on purpose: plain Java, no libraries, no Internet permission, no background services. A change that needs any of those belongs in your own fork.
- The prompt keeps its safety rules: never uninstall, never touch the protected packages, always save a copy first, ask before each change.
- Do not add lists that uninstall packages. Everything here disables with `pm disable-user`, which can be undone.

## Build the launcher

See [docs/BUILD.md](docs/BUILD.md).

## Licence of your contribution

By contributing you agree that your work is released under this repository's [MIT licence](LICENSE).

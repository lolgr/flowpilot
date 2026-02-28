# Flowpilot stand alone
This is a fork of Flowpilot without the need of rooting or a Termux environment. Install the apk and you're done. Requires a Snapdragon based Android phone.

Main features of this fork:
* Standalone apk without additional software requirements
* Android device rooting is not required
* Improved UI resolution

Huge shout out to the original [Flowpilot](https://github.com/flowdriveai/flowpilot) and [Phr00t's fork](https://github.com/phr00t/flowpilot).

# Building notes
Clone:
* `git clone --single-branch --depth 1 https://github.com/jagheterfredrik/flowpilot.git`
* `cd flowpilot`
* `git submodule update --init --recursive`

Setup the environment once:
* Install Docker with [non-root management](https://docs.docker.com/engine/install/linux-postinstall/)
* `./build_env.sh`

Then build using
* `./build.sh full`

One can also build only subcomponents:
* To package the Python code:
  * `./build.sh flowy`
* To build only binary requirements:
  * `./build.sh scons`
* To package the Android app:
  * `./build.sh app`

# Community

[<img src="https://assets-global.website-files.com/6257adef93867e50d84d30e2/636e0b5061df29d55a92d945_full_logo_blurple_RGB.svg" width="200">](https://discord.com/invite/APJaQR9nhz)

Flowpilot's core community lives on the official flowdrive [discord server](https://discord.com/invite/APJaQR9nhz). Check the pinned messages or search history through messages to see if your issues or question has been discussed earlier. You may also join [more awesome](https://linktr.ee/flowdrive) openpilot discord communities. 

We also push frequent updates on our [twitter handle](https://twitter.com/flowdrive_ai).

# Disclaimer 

THIS IS ALPHA QUALITY SOFTWARE FOR RESEARCH PURPOSES ONLY. THIS IS NOT A PRODUCT. YOU ARE RESPONSIBLE FOR COMPLYING WITH LOCAL LAWS AND REGULATIONS. NO WARRANTY EXPRESSED OR IMPLIED.

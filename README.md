# WinLaufen Web

Open-source bridge and local web interface for WinLaufen.

Project initialization in progress.

## Live connection smoke test

The development smoke test checks a live WinLaufen connection on TCP port 4444,
the Java Serialization stream header, and an advancing WinLaufen clock. It is
strictly read-only and sends no application data to WinLaufen.

```sh
./devtools/smoke-winlaufen-clock.sh HOST [PORT]
```

For example, use `./devtools/smoke-winlaufen-clock.sh 192.168.1.20`; the port
defaults to 4444.

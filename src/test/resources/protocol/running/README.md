# Derived Running server stream

`server-stream.bin` is the reassembled server-to-client application byte stream
from `testdata/protocol/running/session.pcapng`. It lets tests exercise the native
`ObjectInputStream` without requiring tshark at test time.

- source PCAP SHA256: `8599e0dcec5dfcfacb851b40108fae047b84ea524e77fbff320111e7af2cd7ce`
- stream SHA256: `96f6caba19403a07ef1881e650951a6a99359f6d6fde649f26b0e06b6abb5c73`
- stream direction: TCP source port 4444, TCP stream 0
- size: 3542 bytes

Extraction (tshark 4.4):

```sh
tshark -r session.pcapng \
  -Y 'tcp.stream==0 && tcp.srcport==4444 && tcp.len>0 && !tcp.analysis.retransmission' \
  -T fields -e tcp.payload | tr -d '\n' | xxd -r -p > server-stream.bin
```

The original PCAP is unchanged.

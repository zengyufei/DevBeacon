# Android Power Policy

Battery conservation is the first design constraint.

## Low-power mode

Low-power mode is the default. Android only makes outbound requests to the PC server unless direct receive is explicitly enabled. It does not listen for UDP broadcast or continuously scan BLE. Long-polling is used so Android can wait on the server without busy polling.

## High availability mode

High availability mode is explicit. It allows shorter polling intervals and BLE fallback policy. The app should present this as a battery tradeoff, not as the default.

## Direct receive mode

Direct receive mode is for CLI direct IP sends without starting the PC server. It is off by default because reliable background receive requires Android to keep an HTTP listener open. When enabled, Android listens on TCP port `8766` at `/notify` and shows its local IP in the production configuration dialog.

## BLE fallback

Low-power mode allows BLE only for manual checks or short recovery windows. Continuous BLE scanning is reserved for high availability mode.

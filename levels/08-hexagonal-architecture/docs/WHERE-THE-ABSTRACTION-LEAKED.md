# Where the Level 1 abstraction leaked

Level 1's port was right for its time: `charge(request)`. Each level bent it a little.

| Level | Pressure | What happened to the port |
|-------|----------|---------------------------|
| 1 | Switch provider by config | `charge()`. Clean |
| 2 | Customer approval | `completeAction()` added: a truth the port can't hide |
| 5 | Lost responses | `getStatus()` added |
| 7 | Six operations | `capture`, `cancel`, `refund`, and credentials on every call |
| 7 | Provider X | **Three methods answering `UNSUPPORTED`; four skipped contract tests** |

The leak wasn't a missing method. **One interface was making a promise no provider could keep:**
"everything here works." Every caller then had to know which providers broke that promise, or find
out at runtime. Adding `UNSUPPORTED` moved the problem from compile time to production.

## What changed

- Before: `gateway.refund(...)` compiled for every provider, and might fail after a network call.
- After: `Refunder` exists only where refunds exist. The router avoids providers that can't serve a
  payment, and `PaymentOperations` refuses before anything is stored or sent.

| | Level 7 | Level 8 |
|---|---------|---------|
| Provider X methods answering `UNSUPPORTED` | 3 | 0 |
| Skipped contract tests | 4 | 0 |
| `switch` on provider names in the core | 1 | 0 |
| Lines of existing Java changed to add a provider | 2 | 0 |

## Two traps avoided

- **Lowest common denominator:** model only what all providers share, and you lose holds and partial
  refunds for everyone.
- **Superset:** model everything anyone can do, and you rebuild the fat interface.

The domain stays between them by describing what the **business** wants (`CaptureMode`), then asking
which provider can do it.

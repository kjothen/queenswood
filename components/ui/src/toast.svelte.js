// App-level toast — one slot, bottom-centre. A second toast replaces
// the first and restarts the timer rather than stacking: these confirm
// an action the operator just took, so the newest is the only one still
// worth reading.
//
//     import { toast, ToastHost } from "@queenswood/ui";
//     toast("Migration approved", "mig.01kz…");
//
// Mount <ToastHost /> once, in the app shell.

let timer = null;
let seq = 0;

export const toastState = $state({ current: null });

export function toast(message, detail) {
  seq += 1;
  toastState.current = { id: seq, message, detail };
  clearTimeout(timer);
  timer = setTimeout(() => {
    toastState.current = null;
  }, 2600);
}

export function dismissToast() {
  clearTimeout(timer);
  toastState.current = null;
}

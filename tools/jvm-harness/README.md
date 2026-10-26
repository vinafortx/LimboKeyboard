# Headless JVM harness

A smoke test for the parts of an Android app that a build cannot check: does every screen survive
being created, measured, laid out, drawn and animated?

```bash
./tools/jvm-harness/run.sh          # lifecycle only (fast)
./tools/jvm-harness/run.sh --draw   # + measure / layout / draw / 180 frames per screen
```

Both languages are covered (ru, en), all four screens are exercised, and the keyboard gets its own
pass: `onCreateInputView`, a draw, `onStartInputView`, more frames, `onFinishInputView`. The run
ends with the safety net: the crash report round-trip and the plain fallback board.

## What it is

`shim/` is a small stand-in for the Android framework — just enough of `Activity`, `View`,
`ViewGroup`, `Canvas`, `Paint`, `Choreographer`, `Handler`, `InputMethodService`, `SharedPreferences`
and `org.json` to let the real application code run on a desktop JVM. The app itself is compiled
against a real `android.jar`, exactly like the release build, so the shim cannot hide a wrong
signature.

`harness/Main.kt` drives the application: it creates `LimboApp`, then each screen through its full
lifecycle, with a simulated 60 fps clock, then the input method.

## What it proves, and what it does not

It catches: lifecycle mistakes, crashes while building a view tree, exceptions thrown from
`onDraw`, broken frame callbacks, and the recovery paths added for failures.

It cannot catch: anything that depends on real resources, real font metrics, real bitmaps and
skia behaviour, system services (`VibratorManager`, `WindowInsetsController`), or OEM quirks. The
shim reports zero sizes and empty strings on purpose — a screen that only works when a number is
non-zero is a screen with a bug.

When a stage fails it prints the exception with the frames that belong to the app.

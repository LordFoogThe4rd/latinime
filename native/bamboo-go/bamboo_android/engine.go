// Instance-based shim for bamboo-core (Go), replacing the Rust port.
//
// Upstream bamboo-core is instance-based with zero global state. The JNI
// layer (jni_glue.c) hands out opaque jlong handles; this file owns
// the handle registry so the Go GC can reclaim freed engines and handles are
// stable across JNI calls. Method map: 0 = Telex, 1 = VNI.
//
// Backspace semantics:
//   - EngineRemoveLastChar         — keystroke pop, the default backspace.
//     Go's RemoveLastChar removes the last *output character*, not the last
//     keystroke, so keystroke pop is reproduced with the public API:
//     RestoreLastWord(false) (break the last word into raw keystrokes),
//     RemoveLastChar(false) (drop the last raw keystroke),
//     RestoreLastWord(true) (re-process the raw keystrokes).
//   - EngineRemoveLastOutputChar   — whole-char backspace:
//     RemoveLastChar(true), tone retargeted per std tone style.
package main

/*
#cgo android LDFLAGS: -llog
#include <stdlib.h>
*/
import "C"

import (
	"sync"
	"unsafe"

	"github.com/BambooEngine/bamboo-core"
)

// engineOutputString returns the current composition as a Go string,
// converting the malloc-allocated C string returned by EngineOutput. Host
// tests cannot import "C" (go/build rejects cgo in _test.go files), so they
// call this instead of EngineOutput directly.
func engineOutputString(handle int64) string {
	out := EngineOutput(handle)
	if out == nil {
		return ""
	}
	defer C.free(unsafe.Pointer(out))
	return C.GoString(out)
}

var methodNames = map[int64]string{
	0: "Telex",
	1: "VNI",
}

var (
	enginesMu sync.Mutex
	engines   = make(map[int64]bamboo.IEngine)
	nextID    int64 = 1
)

// EngineNew creates an engine for the given input method (0 = Telex,
// 1 = VNI) and returns its handle, or 0 on unknown method.
//
//export EngineNew
func EngineNew(method int64) int64 {
	name, ok := methodNames[method]
	if !ok {
		return 0
	}
	im := bamboo.ParseInputMethod(bamboo.InputMethodDefinitions, name)
	engine := bamboo.NewEngine(im, bamboo.EstdFlags)

	enginesMu.Lock()
	defer enginesMu.Unlock()
	id := nextID
	nextID++
	engines[id] = engine
	return id
}

// EngineProcess feeds one code point to the engine and returns the current
// composition (preedit) as a malloc-allocated C string; the C caller must
// free() it. Returns NULL for an invalid handle. A Go string must never be
// returned directly from an exported cgo function: since Go 1.21 the runtime
// aborts with "unpinned Go string" because the backing array may be moved by
// the GC (the C glue in jni_glue.c passes the pointer straight to free()).
//
//export EngineProcess
func EngineProcess(handle int64, cp int32) *C.char {
	engine := lookup(handle)
	if engine == nil {
		return nil
	}
	engine.ProcessKey(rune(cp), bamboo.VietnameseMode)
	return C.CString(engine.GetProcessedString(bamboo.VietnameseMode))
}

// EngineOutput returns the current composition (preedit) as a
// malloc-allocated C string; the C caller must free() it. Returns NULL for an
// invalid handle.
//
//export EngineOutput
func EngineOutput(handle int64) *C.char {
	engine := lookup(handle)
	if engine == nil {
		return nil
	}
	return C.CString(engine.GetProcessedString(bamboo.VietnameseMode))
}

// EngineRemoveLastChar pops the last keystroke (default backspace) via the
// RestoreLastWord replay trick.
//
//export EngineRemoveLastChar
func EngineRemoveLastChar(handle int64) {
	engine := lookup(handle)
	if engine == nil {
		return
	}
	engine.RestoreLastWord(false)
	engine.RemoveLastChar(false)
	engine.RestoreLastWord(true)
}

// EngineRemoveLastOutputChar removes the last output character, retargeting
// the tone per std tone style.
//
//export EngineRemoveLastOutputChar
func EngineRemoveLastOutputChar(handle int64) {
	engine := lookup(handle)
	if engine == nil {
		return
	}
	engine.RemoveLastChar(true)
}

// EngineReset clears the current composition.
//
//export EngineReset
func EngineReset(handle int64) {
	engine := lookup(handle)
	if engine == nil {
		return
	}
	engine.Reset()
}

// EngineFree releases the engine; its handle is no longer valid.
//
//export EngineFree
func EngineFree(handle int64) {
	enginesMu.Lock()
	defer enginesMu.Unlock()
	delete(engines, handle)
}

func lookup(handle int64) bamboo.IEngine {
	enginesMu.Lock()
	defer enginesMu.Unlock()
	return engines[handle]
}

// main is required by the linker for -buildmode=c-shared builds of a main
// package (the runtime references main.main); the library entry points are
// the cgo-exported Engine* functions above.
func main() {}

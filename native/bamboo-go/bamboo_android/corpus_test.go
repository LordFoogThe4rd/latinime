// Corpus for the bamboo-go shim (`go test`, host).
//
// 46 cases first made for nguyen10t2/bamboo_core (Rust) at
// native/bamboo/bamboo_android/src/corpus.rs, then converted and kept
// when migrating to BambooEngine/bamboo-core (Go). They come from vi-rs
// edge cases and upstream bamboo docs. expected = Go results
// (BambooEngine is the source of truth); every case matched Go
// byte-for-byte.
//
// Plus regression cases: whole-char vs keystroke backspace,
// tone-toggle, uppercase-reset, and combiner quirks at engine
// level (C3/C5).
package main

import (
	"testing"
)

func engineOutput(handle int64) string {
	return engineOutputString(handle)
}

type corpusCase struct {
	id       string
	method   int64
	input    string
	expected string
}

var telexCases = []corpusCase{
	{"T1", 0, "vietej", "việt"},
	{"T2", 0, "VIETEJ", "VIỆT"},
	{"T3", 0, "Vietej", "Việt"},
	{"T4", 0, "huow", "huơ"},
	{"T5", 0, "uow", "uơ"},
	{"T6", 0, "uwow", "ươ"},
	{"T7", 0, "gija", "giạ"},
	{"T8", 0, "oeo", "oeo"},
	{"T9", 0, "oaw", "oă"},
	{"T10", 0, "aa", "â"},
	{"T11", 0, "ee", "ê"},
	{"T12", 0, "oo", "ô"},
	{"T13", 0, "dd", "đ"},
	{"T14", 0, "ddi", "đi"},
	{"T15", 0, "dddi", "ddi"},
	{"T16", 0, "hoas", "hóa"},
	{"T17", 0, "hoes", "hóe"},
	{"T18", 0, "hoos", "hố"},
	{"T19", 0, "huys", "húy"},
	{"T20", 0, "huos", "húo"},
	{"T21", 0, "gies", "gié"},
	{"T22", 0, "aso", "áo"},
	{"T23", 0, "sao", "sao"},
	{"T24", 0, "quanf", "quàn"},
	{"T25", 0, "tieengs", "tiếng"},
}

var vniCases = []corpusCase{
	{"V1", 1, "viet56", "việt"},
	{"V2", 1, "VIET56", "VIỆT"},
	{"V3", 1, "Viet56", "Việt"},
	{"V4", 1, "gi5a", "giạ"},
	{"V5", 1, "a6", "â"},
	{"V6", 1, "a8", "ă"},
	{"V7", 1, "d9", "đ"},
	{"V8", 1, "e6", "ê"},
	{"V9", 1, "o6", "ô"},
	{"V10", 1, "o7", "ơ"},
	{"V11", 1, "u7", "ư"},
	{"V12", 1, "uu7", "ưu"},
	{"V13", 1, "uou7", "ươu"},
	{"V14", 1, "hoa1", "hóa"},
	{"V15", 1, "hoa2", "hòa"},
	{"V16", 1, "hoa3", "hỏa"},
	{"V17", 1, "hoa4", "hõa"},
	{"V18", 1, "hoa5", "họa"},
	{"V19", 1, "hoe1", "hóe"},
	{"V20", 1, "qua1", "quá"},
	{"V21", 1, "uong7", "ương"},
}

func runCase(t *testing.T, c corpusCase) string {
	t.Helper()
	handle := EngineNew(c.method)
	if handle == 0 {
		t.Fatalf("%s: EngineNew(%d) returned 0", c.id, c.method)
	}
	defer EngineFree(handle)
	for _, r := range c.input {
		EngineProcess(handle, int32(r))
	}
	return engineOutput(handle)
}

func TestTelexCorpus(t *testing.T) {
	for _, c := range telexCases {
		if got := runCase(t, c); got != c.expected {
			t.Errorf("%s: input %q: got %q, want %q", c.id, c.input, got, c.expected)
		}
	}
}

func TestVNICorpus(t *testing.T) {
	for _, c := range vniCases {
		if got := runCase(t, c); got != c.expected {
			t.Errorf("%s: input %q: got %q, want %q", c.id, c.input, got, c.expected)
		}
	}
}

func typeString(handle int64, s string) {
	for _, r := range s {
		EngineProcess(handle, int32(r))
	}
}

func TestBackspaceKeystrokePop(t *testing.T) {
	// C3: vietej -> việt, DEL -> viêt, DEL -> viet
	handle := EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "vietej")
	if got := engineOutput(handle); got != "việt" {
		t.Fatalf("vietej: got %q", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "viêt" {
		t.Fatalf("vietej DEL: got %q, want viêt", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "viet" {
		t.Fatalf("vietej DEL DEL: got %q, want viet", got)
	}

	// tiengs -> tiéng, DEL pops the tone key -> tieng
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "tiengs")
	if got := engineOutput(handle); got != "tiéng" {
		t.Fatalf("tiengs: got %q, want tiéng", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "tieng" {
		t.Fatalf("tiengs DEL: got %q, want tieng", got)
	}

	// VNI: viet56 -> việt, DEL undoes the last key -> viẹt
	handle = EngineNew(1)
	defer EngineFree(handle)
	typeString(handle, "viet56")
	if got := engineOutput(handle); got != "việt" {
		t.Fatalf("viet56: got %q", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "viẹt" {
		t.Fatalf("viet56 DEL: got %q, want viẹt", got)
	}

	// loajn -> loạn, DEL pops the 'n' and the replay re-marks the tone per
	// std style -> lọa (Go engine; the Rust snapshot stack gave "loan")
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "loajn")
	if got := engineOutput(handle); got != "loạn" {
		t.Fatalf("loajn: got %q, want loạn", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "lọa" {
		t.Fatalf("loajn DEL: got %q, want lọa", got)
	}

	// Plain characters pop one by one
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "viet")
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "vie" {
		t.Fatalf("viet DEL: got %q, want vie", got)
	}
}

func TestBackspaceWholeChar(t *testing.T) {
	// tieesng -> tiếng, whole-char DEL drops the final grapheme, tone on ê kept
	handle := EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "tieesng")
	if got := engineOutput(handle); got != "tiếng" {
		t.Fatalf("tieesng: got %q", got)
	}
	EngineRemoveLastOutputChar(handle)
	if got := engineOutput(handle); got != "tiến" {
		t.Fatalf("tieesng DEL: got %q, want tiến", got)
	}

	// tiengs -> tiéng (tone on plain e), whole-char DEL -> tién
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "tiengs")
	EngineRemoveLastOutputChar(handle)
	if got := engineOutput(handle); got != "tién" {
		t.Fatalf("tiengs DEL: got %q, want tién", got)
	}

	// loajn -> loạn, whole-char DEL retargets the tone per std tone style
	// (2-vowel nucleus, no coda -> first vowel) -> lọa; without refresh the
	// tone keeps its position -> loạ
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "loajn")
	EngineRemoveLastOutputChar(handle)
	if got := engineOutput(handle); got != "lọa" {
		t.Fatalf("loajn DEL(true): got %q, want lọa", got)
	}

	// VNI: viet65 -> việt, whole-char DEL keeps the ê mark and nặng tone -> việ
	handle = EngineNew(1)
	defer EngineFree(handle)
	typeString(handle, "viet65")
	if got := engineOutput(handle); got != "việt" {
		t.Fatalf("viet65: got %q", got)
	}
	EngineRemoveLastOutputChar(handle)
	if got := engineOutput(handle); got != "việ" {
		t.Fatalf("viet65 DEL: got %q, want việ", got)
	}

	// Single-grapheme composition empties out
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "aa")
	if got := engineOutput(handle); got != "â" {
		t.Fatalf("aa: got %q", got)
	}
	EngineRemoveLastOutputChar(handle)
	if got := engineOutput(handle); got != "" {
		t.Fatalf("aa DEL: got %q, want empty", got)
	}
}

func TestToneToggle(t *testing.T) {
	// Double effect key undoes it: hef -> hè, heff -> hef
	handle := EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "heff")
	if got := engineOutput(handle); got != "hef" {
		t.Fatalf("heff: got %q, want hef", got)
	}

	// viet + s + s then DEL: the second s toggles the tone off (viet+s+s ->
	// viets in Go, with the raw s appended — the Rust plan expected a pure
	// undo to "viet"); DEL then pops the raw s -> viét
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "viets")
	if got := engineOutput(handle); got != "viét" {
		t.Fatalf("viets: got %q", got)
	}
	typeString(handle, "s")
	if got := engineOutput(handle); got != "viets" {
		t.Fatalf("viets+s: got %q, want viets", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "viét" {
		t.Fatalf("viets+s DEL: got %q, want viét", got)
	}

	// Surrfface: Go's actual result is Surfface — the second f appends
	// instead of toggling because the composition after the r-toggle holds a
	// remove-tone transformation. The Rust
	// port's patched expectation ("Surface") does not hold for the Go engine;
	// BambooEngine is the source of truth.
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "Surrfface")
	if got := engineOutput(handle); got != "Surfface" {
		t.Fatalf("Surrfface: got %q, want Surfface", got)
	}

	// Tone keys replace each other: Sufr -> Sủ (huyền overridden by hỏi)
	handle = EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "Sufr")
	if got := engineOutput(handle); got != "Sủ" {
		t.Fatalf("Sufr: got %q, want Sủ", got)
	}
}

func TestUppercaseResetCycles(t *testing.T) {
	// Old Rust bug class (stale-DFA fast path after uppercase keys + reset)
	// is structurally impossible in Go: no lazy-JIT DFA, no snapshots.
	handle := EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "Cas")
	if got := engineOutput(handle); got != "Cá" {
		t.Fatalf("Cas: got %q, want Cá", got)
	}
	EngineReset(handle)
	if got := engineOutput(handle); got != "" {
		t.Fatalf("reset: got %q, want empty", got)
	}
	EngineProcess(handle, 'a')
	if got := engineOutput(handle); got != "a" {
		t.Fatalf("a after reset: got %q, want a", got)
	}
	EngineProcess(handle, 'a')
	if got := engineOutput(handle); got != "â" {
		t.Fatalf("aa after reset: got %q, want â", got)
	}

	EngineReset(handle)
	EngineProcess(handle, 'a')
	if got := engineOutput(handle); got != "a" {
		t.Fatalf("a after 2nd reset: got %q, want a", got)
	}
	EngineReset(handle)
	typeString(handle, "Baf")
	if got := engineOutput(handle); got != "Bà" {
		t.Fatalf("Baf: got %q, want Bà", got)
	}
	EngineReset(handle)
	EngineProcess(handle, 'a')
	if got := engineOutput(handle); got != "a" {
		t.Fatalf("a after Baf reset: got %q, want a", got)
	}
}

func TestCombinerQuirksAtEngineLevel(t *testing.T) {
	// C5: backspace to empty then retype — fresh composition
	handle := EngineNew(0)
	defer EngineFree(handle)
	typeString(handle, "vietej")
	for i := 0; i < 6; i++ {
		EngineRemoveLastChar(handle)
	}
	if got := engineOutput(handle); got != "" {
		t.Fatalf("backspace to empty: got %q, want empty", got)
	}
	typeString(handle, "x")
	if got := engineOutput(handle); got != "x" {
		t.Fatalf("retype: got %q, want x", got)
	}

	// Backspace pops the last keystroke even from a single plain char, and
	// backspace on an empty composition is a no-op (Go's RemoveLastChar
	// always changes the composition when there is one; the Rust-era phantom
	// guard in the combiners is being deleted for this reason)
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "" {
		t.Fatalf("backspace on x: got %q, want empty", got)
	}
	EngineRemoveLastChar(handle)
	if got := engineOutput(handle); got != "" {
		t.Fatalf("backspace on empty: got %q, want empty", got)
	}

	// VNI ASCII digits are engine keys (the combiner decides whether to pass
	// them through — C1/C2 are combiner-level); at engine level 5/6 transform
	handle = EngineNew(1)
	defer EngineFree(handle)
	typeString(handle, "viet5")
	if got := engineOutput(handle); got != "viẹt" {
		t.Fatalf("viet5: got %q, want viẹt", got)
	}
}

func TestHandleRegistry(t *testing.T) {
	// Unknown method -> 0
	if handle := EngineNew(2); handle != 0 {
		t.Fatalf("EngineNew(2): got %d, want 0", handle)
	}

	// Invalid handle is a no-op
	EngineProcess(0, 'a')
	EngineRemoveLastChar(0)
	EngineRemoveLastOutputChar(0)
	EngineReset(0)
	EngineFree(0)
	if got := engineOutput(0); got != "" {
		t.Fatalf("invalid handle output: got %q, want empty", got)
	}

	// Two engines coexist and are independent (Telex + VNI simultaneously)
	t1 := EngineNew(0)
	t2 := EngineNew(1)
	defer EngineFree(t1)
	defer EngineFree(t2)
	typeString(t1, "hoas")
	typeString(t2, "hoa1")
	if got := engineOutput(t1); got != "hóa" {
		t.Fatalf("telex hoas: got %q", got)
	}
	if got := engineOutput(t2); got != "hóa" {
		t.Fatalf("vni hoa1: got %q", got)
	}
	typeString(t1, "x")
	if got := engineOutput(t1); got != "hõa" {
		t.Fatalf("telex hoasx: got %q, want hõa", got)
	}
	if got := engineOutput(t2); got != "hóa" {
		t.Fatalf("vni after telex continues: got %q", got)
	}

	// Freed handle becomes a no-op
	freeMe := EngineNew(0)
	typeString(freeMe, "aa")
	EngineFree(freeMe)
	if got := engineOutput(freeMe); got != "" {
		t.Fatalf("freed handle output: got %q, want empty", got)
	}

	// Handles never collide across new/free cycles
	h1 := EngineNew(0)
	EngineFree(h1)
	h2 := EngineNew(1)
	defer EngineFree(h2)
	if h1 == h2 {
		t.Fatalf("handle %d reused before h2 allocated", h1)
	}
}

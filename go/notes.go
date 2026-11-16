package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"
)

type Note struct {
	Text string    `json:"text"`
	Host string    `json:"host"`
	At   time.Time `json:"at"`
}

type NoteInput struct {
	Text string `json:"text" required:"true" maxLength:"280"`
}

var (
	errNoDataDir = errors.New("notes are off: set DATA_DIR and mount a volume there")
	errReadOnly  = errors.New("the volume at DATA_DIR is mounted read-only")
)

type noteStore struct {
	dir string
	mu  sync.Mutex
}

func (s *noteStore) path() string { return filepath.Join(s.dir, "notes.jsonl") }

func (s *noteStore) List() ([]Note, error) {
	if s.dir == "" {
		return nil, errNoDataDir
	}
	f, err := os.Open(s.path())
	if errors.Is(err, os.ErrNotExist) {
		return []Note{}, nil
	}
	if err != nil {
		return nil, err
	}
	defer f.Close()
	notes := []Note{}
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		var n Note
		if json.Unmarshal(sc.Bytes(), &n) == nil {
			notes = append(notes, n)
		}
	}
	return notes, sc.Err()
}

func (s *noteStore) Add(text, host string) (Note, error) {
	if s.dir == "" {
		return Note{}, errNoDataDir
	}
	n := Note{Text: strings.TrimSpace(text), Host: host, At: time.Now().UTC()}
	line, err := json.Marshal(n)
	if err != nil {
		return Note{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	// One write per line with O_APPEND keeps lines whole when several replicas share the volume.
	f, err := os.OpenFile(s.path(), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return Note{}, classify(err)
	}
	defer f.Close()
	if _, err := f.Write(append(line, '\n')); err != nil {
		return Note{}, classify(err)
	}
	return n, nil
}

func classify(err error) error {
	if errors.Is(err, syscall.EROFS) {
		return errReadOnly
	}
	return err
}

func readBanner(path string) string {
	if path == "" {
		return ""
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(b))
}

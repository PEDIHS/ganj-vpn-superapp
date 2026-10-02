//go:build android

package libXray

import (
    "errors"
    "os"
    "strconv"
)

// SetTunFd binds the VpnService-owned TUN descriptor before runXrayFromJson.
// The descriptor stays owned by the Android service: Go must never close it.
// It is process-scoped, so callers must serialize engine start/stop operations.
func SetTunFd(fd int) error {
    if fd < 0 {
        return errors.New("invalid Android TUN file descriptor")
    }
    return os.Setenv("XRAY_TUN_FD", strconv.Itoa(fd))
}

// ResetTunFd drops the stale binding after the native core stops.
func ResetTunFd() {
    _ = os.Unsetenv("XRAY_TUN_FD")
}

// Transcript page script. Loaded in <head> so the inline onerror handlers below exist before any image loads.
// Lines are trimmed and // comments stripped at load time, so every statement ends with a semicolon.
(function () {
    "use strict";
    var NOW = Date.now();

    function isExpired(el) {
        var ex = el && el.getAttribute("data-ex");
        return !!ex && parseInt(ex, 10) * 1000 <= NOW;
    }

    // Replace a media box with the "expired / unavailable" placeholder (or mark a file card as expired).
    function gone(box) {
        if (!box || box.getAttribute("data-gone")) return;
        box.setAttribute("data-gone", "1");
        var expired = isExpired(box);
        var label = box.getAttribute("data-label") || "Attachment";
        var kind = box.getAttribute("data-kind");
        if (kind === "file" || kind === "audio") {
            box.classList.add("expired");
            var link = box.querySelector("a.file-name");
            if (link) {
                var span = document.createElement("span");
                span.className = "file-name";
                span.textContent = link.textContent;
                link.replaceWith(span);
            }
            var audio = box.querySelector("audio");
            if (audio) audio.remove();
            var meta = box.querySelector(".file-size");
            if (meta) meta.textContent = [box.getAttribute("data-size"), expired ? "link expired" : "unavailable"].filter(Boolean).join(" · ");
            if (expired) showNotice();
            return;
        }
        var tpl = document.getElementById("tpl-gone");
        if (!tpl) { box.removeAttribute("data-gone"); return; }
        var ph = tpl.content.firstElementChild.cloneNode(true);
        ph.querySelector(".gone-title").textContent = label + (expired ? " expired" : " unavailable");
        ph.querySelector(".gone-name").textContent = [box.getAttribute("data-name"), box.getAttribute("data-size")].filter(Boolean).join(" · ");
        ph.querySelector(".gone-note").textContent = expired
            ? "Discord attachment links expire about 24 hours after they're created."
            : "This file could not be loaded. It may have been deleted.";
        var style = box.getAttribute("style");
        if (style) ph.setAttribute("style", style);
        box.classList.forEach(function (c) { if (c !== "media" && c !== "video" && c !== "spoiler-media") ph.classList.add(c); });
        box.replaceWith(ph);
        if (expired) showNotice();
    }

    function showNotice() {
        var n = document.getElementById("expiry-notice");
        if (n) n.hidden = false;
    }

    // Media failed to load: try the fallback URL once, then show the placeholder.
    window.__mf = function (el) {
        var fb = el.getAttribute("data-fallback");
        if (fb) {
            el.removeAttribute("data-fallback");
            el.src = fb;
            var a = el.closest("a");
            if (a) a.href = fb;
            return;
        }
        gone(el.closest("[data-media]"));
    };

    // Avatar failed: fall back to Discord's default avatar, then give up quietly.
    window.__af = function (img) {
        var def = img.getAttribute("data-def");
        if (def && img.src !== def) {
            img.src = def;
        } else {
            img.onerror = null;
            img.style.visibility = "hidden";
        }
    };

    // Emoji or sticker failed: show its name as text instead.
    window.__ef = function (img) {
        var s = document.createElement("span");
        s.textContent = img.alt || "";
        img.replaceWith(s);
    };

    function pad(n) { return (n < 10 ? "0" : "") + n; }

    function relative(ms) {
        var diff = Math.round((ms - Date.now()) / 1000);
        var abs = Math.abs(diff);
        var units = [["year", 31536000], ["month", 2592000], ["day", 86400], ["hour", 3600], ["minute", 60], ["second", 1]];
        for (var i = 0; i < units.length; i++) {
            if (abs >= units[i][1] || units[i][0] === "second") {
                var value = Math.round(diff / units[i][1]);
                try { return new Intl.RelativeTimeFormat(undefined, { numeric: "auto" }).format(value, units[i][0]); }
                catch (e) { return null; }
            }
        }
        return null;
    }

    function format(ms, f) {
        var d = new Date(ms);
        try {
            switch (f) {
                case "t": return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
                case "T": return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
                case "d": return d.toLocaleDateString(undefined, { day: "2-digit", month: "2-digit", year: "numeric" });
                case "D": return d.toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });
                case "F": return d.toLocaleString(undefined, { weekday: "long", day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" });
                case "R": return relative(ms);
                case "m": return d.toLocaleDateString(undefined, { day: "2-digit", month: "2-digit", year: "numeric" }) + " " + pad(d.getHours()) + ":" + pad(d.getMinutes());
                default: return d.toLocaleString(undefined, { day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" });
            }
        } catch (e) {
            return null;
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        // Links we already know are dead: swap them out without waiting for a failed request.
        document.querySelectorAll("[data-media][data-ex]").forEach(function (box) {
            if (isExpired(box)) gone(box);
        });
        var notice = document.getElementById("expiry-notice");
        if (notice && isExpired(notice)) notice.hidden = false;

        // Show every timestamp in the viewer's own locale and time zone.
        document.querySelectorAll("time[data-ts]").forEach(function (t) {
            var ms = parseInt(t.getAttribute("data-ts"), 10);
            var text = format(ms, t.getAttribute("data-f"));
            if (text) t.textContent = text;
            var full = format(ms, "F");
            if (full) t.title = full;
        });

        // Click to reveal spoilered media and containers.
        document.addEventListener("click", function (e) {
            var s = e.target.closest && e.target.closest(".spoiler-media");
            if (s) {
                e.preventDefault();
                s.classList.remove("spoiler-media");
            }
        }, true);
    });
})();

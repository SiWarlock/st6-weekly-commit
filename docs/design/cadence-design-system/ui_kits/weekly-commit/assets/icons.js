/* =========================================================================
   ST6 Weekly Commit — Icon set
   Heroicons family (per spec: react-icons/hi), rendered OUTLINE @ 24×24,
   stroke 1.75, currentColor — tuned to Linear's thin-line aesthetic.
   Canonical name = the Heroicons identifier used in the spec (§4.2 / §4.6).
   Single source for both plain-HTML preview cards and the JSX UI kit.
   ========================================================================= */
(function () {
  // d = path data; fill:true => filled glyph (no stroke)
  var I = {
    // ---- plan lifecycle ----
    HiPencilAlt:       { d: "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" },
    HiLockClosed:      { d: "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" },
    HiClipboardCheck:  { d: "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" },
    HiCheckCircle:     { d: "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0" },
    HiMinusCircle:     { d: "M15 12H9m12 0a9 9 0 11-18 0 9 9 0 0118 0" },

    // ---- review / status ----
    HiOutlineEye:      { d: "M15 12a3 3 0 11-6 0 3 3 0 016 0zM2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" },
    HiExclamationCircle:{ d: "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0" },
    HiClock:           { d: "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0" },
    HiCheck:           { d: "M5 13l4 4L19 7" },
    HiX:               { d: "M6 18L18 6M6 6l12 12" },
    HiXCircle:         { d: "M9 9l6 6m0-6l-6 6M21 12a9 9 0 11-18 0 9 9 0 0118 0" },
    HiQuestionMarkCircle:{ d: "M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0" },
    HiExclamation:     { d: "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" },

    // ---- risk / reconciliation / work-type ----
    HiBan:             { d: "M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" },
    HiArrowNarrowRight:{ d: "M17 8l4 4m0 0l-4 4m4-4H3" },
    HiAdjustments:     { d: "M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" },
    HiSparkles:        { d: "M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" },
    HiCog:             { d: "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z" },
    HiPlusCircle:      { d: "M12 8v8m-4-4h8M21 12a9 9 0 11-18 0 9 9 0 0118 0" },

    // ---- dispute ----
    HiFlag:            { d: "M3 21v-4m0 0V5a2 2 0 012-2h6.5l1 1H21l-3 6 3 6h-8.5l-1-1H5a2 2 0 00-2 2z" },
    HiReply:           { d: "M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6" },

    // ---- utility ----
    HiChevronRight:    { d: "M9 5l7 7-7 7" },
    HiChevronLeft:     { d: "M15 19l-7-7 7-7" },
    HiChevronDown:     { d: "M19 9l-7 7-7-7" },
    HiSelector:        { d: "M8 9l4-4 4 4m0 6l-4 4-4-4" },
    HiFilter:          { d: "M3 4a1 1 0 011-1h16a1 1 0 011 1v2a1 1 0 01-.293.707L15 13.414V19a1 1 0 01-.293.707l-2 2A1 1 0 0111 21v-7.586L3.293 6.707A1 1 0 013 6V4z" },
    HiChat:            { d: "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" },
    HiInformationCircle:{ d: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0" },
    HiRefresh:         { d: "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" },
    HiCalendar:        { d: "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" },
    HiExternalLink:    { d: "M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" },
    HiSearch:          { d: "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" },
    HiPlus:            { d: "M12 4v16m8-8H4" },
    HiTrash:           { d: "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" },
    HiPencil:          { d: "M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" },
    HiDotsHorizontal:  { fill: true, d: "M6 12a1.4 1.4 0 11-2.8 0 1.4 1.4 0 012.8 0zm7.4 0a1.4 1.4 0 11-2.8 0 1.4 1.4 0 012.8 0zm7.4 0a1.4 1.4 0 11-2.8 0 1.4 1.4 0 012.8 0z" },
    HiUserGroup:       { d: "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" },
    HiViewGrid:        { d: "M4 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1v-4zM14 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z" }
  };

  function svg(name, opts) {
    opts = opts || {};
    var ic = I[name];
    if (!ic) { ic = I.HiInformationCircle; }
    var size = opts.size || 16;
    var sw = opts.strokeWidth || 1.75;
    var cls = opts.class ? ' class="' + opts.class + '"' : '';
    var style = opts.style ? ' style="' + opts.style + '"' : '';
    if (ic.fill) {
      return '<svg' + cls + style + ' width="' + size + '" height="' + size +
        '" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="' +
        ic.d + '"/></svg>';
    }
    return '<svg' + cls + style + ' width="' + size + '" height="' + size +
      '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' + sw +
      '" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' +
      ic.d + '"/></svg>';
  }

  if (typeof window !== "undefined") {
    window.WC_ICONS = I;
    window.wcIcon = svg;
  }
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { ICONS: I, svg: svg };
  }
})();

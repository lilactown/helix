function $RC(a, b) {
  a = document.getElementById(a);
  b = document.getElementById(b);
  b.parentNode.removeChild(b);
  if (a) {
    a = a.previousSibling;
    var f = a.parentNode,
      c = a.nextSibling,
      e = 0;
    do {
      if (c && 8 === c.nodeType) {
        var d = c.data;
        if ("/$" === d)
          if (0 === e) break;
          else e--;
        else ("$" !== d && "$?" !== d && "$!" !== d) || e++;
      }
      d = c.nextSibling;
      f.removeChild(c);
      c = d;
    } while (c);
    for (; b.firstChild; ) f.insertBefore(b.firstChild, c);
    a.data = "$";
    a._reactRetry && a._reactRetry();
  }
}

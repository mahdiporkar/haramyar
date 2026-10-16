// Small usability layer for mobile-number usernames. The server normalizes authoritatively (haramyar-keycloak-mobile-
// username provider); this only offers the phone keypad and converts Persian/Arabic digits while typing.
(function () {
  function asciiDigits(value) {
    return value.replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06f0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); });
  }
  function enhance() {
    var username = document.getElementById('username');
    if (!username) return;
    username.setAttribute('inputmode', 'tel');
    username.setAttribute('autocomplete', 'username');
    username.setAttribute('dir', 'ltr');
    username.addEventListener('input', function () {
      var converted = asciiDigits(username.value);
      if (converted !== username.value) username.value = converted;
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', enhance);
  else enhance();
})();

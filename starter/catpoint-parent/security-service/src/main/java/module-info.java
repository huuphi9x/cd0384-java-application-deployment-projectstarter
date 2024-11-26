module com.udacity.catpoint.securitymodule {
  requires com.udacity.catpoint.imagemodule;
  opens com.udacity.catpoint.security.data to com.google.gson;

  requires com.google.common;
  requires com.google.gson;
  requires java.desktop;
  requires java.prefs;
  requires miglayout;
}

module com.udacity.catpoint.security {
  requires com.udacity.catpoint.image;
  opens com.udacity.catpoint.security.data to com.google.gson;

  requires com.google.common;
  requires com.google.gson;
  requires java.desktop;
  requires java.prefs;
  requires miglayout;
}

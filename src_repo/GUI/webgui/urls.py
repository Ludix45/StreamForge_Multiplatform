# 06.06.25

from django.contrib import admin
from django.urls import path, include, re_path
from django.contrib.staticfiles.views import serve as _static_serve

urlpatterns = [
    path("admin/", admin.site.urls),
    path("", include("searchapp.urls")),
]

urlpatterns += [
    re_path(
        r"^static/(?P<path>.*)$",
        lambda request, path: _static_serve(request, path, insecure=True),
    ),
]

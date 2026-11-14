import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { blockPageZoom } from './app/core/no-page-zoom';

blockPageZoom(document);
bootstrapApplication(App, appConfig).catch((err) => console.error(err));

import {defineConfig} from '@playwright/test';
import base from './playwright.config.js';
export default defineConfig({...base, testMatch: 'ai-workflow.check.js'});

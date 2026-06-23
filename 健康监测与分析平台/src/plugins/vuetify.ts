import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

export const vuetify = createVuetify({
  components,
  directives,
  defaults: {
    VBtn: {
      rounded: 'lg',
      variant: 'flat',
    },
    VCard: {
      rounded: 'xl',
      elevation: 0,
    },
    VTextField: {
      variant: 'outlined',
      rounded: 'lg',
    },
    VSelect: {
      variant: 'outlined',
      rounded: 'lg',
    },
    VTabs: {
      color: 'teal-darken-2',
    },
  },
  theme: {
    defaultTheme: 'healthTheme',
    themes: {
      healthTheme: {
        dark: false,
        colors: {
          primary: '#115e59',
          secondary: '#0f766e',
          accent: '#14b8a6',
          success: '#10b981',
          warning: '#f59e0b',
          error: '#ef4444',
          info: '#0ea5e9',
          background: '#f4f8f6',
          surface: '#ffffff',
        },
      },
    },
  },
})

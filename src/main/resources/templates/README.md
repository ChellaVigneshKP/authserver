# Thymeleaf Templates

This directory contains all Thymeleaf templates for the Auth Server application.

## Directory Structure

```
templates/
├── fragments/          # Reusable template fragments
│   ├── header.html    # Common HTML head with styles and meta tags
│   ├── footer.html    # Common footer with links and GTM
│   ├── sidebar.html   # Progress sidebar for multi-step flows
│   └── scripts.html   # Google Tag Manager scripts
│
└── pages/             # Page templates
    ├── 404.html                              # Error page
    ├── login/
    │   └── login.html                        # Main login page
    ├── login-legacy.html                     # Legacy login page
    ├── mfa/
    │   ├── request-pin.html                  # MFA PIN request page
    │   └── enter-pin.html                    # MFA PIN entry page
    ├── mfa-legacy.html                       # Legacy MFA page
    ├── account-access/
    │   ├── identify-yourself.html            # Forgot password/Activate account
    │   └── update-password.html              # Password reset page
    ├── forgot-username/
    │   └── identify-yourself.html            # Forgot username page
    ├── mobile-forgot-username/
    │   └── identify-yourself.html            # Mobile forgot username page
    └── user/
        ├── change-password.html              # User password change
        └── change-profile.html               # User profile update
```

## Features

### Modern Design
- Clean, professional UI with modern aesthetics
- Consistent color scheme using CSS custom properties
- Card-based layouts for better visual hierarchy
- Smooth transitions and hover effects

### Responsive Layout
- Mobile-first design approach
- Flexible grid layouts that adapt to screen size
- Touch-friendly form controls and buttons
- Optimized for tablets and desktops

### Accessibility
- Proper semantic HTML structure
- ARIA attributes where necessary
- Keyboard navigation support
- Clear focus indicators
- High contrast ratios for text

### Customization
- Brand colors via model attributes (planPrimaryColor, planSecondaryColor, etc.)
- Dynamic content via CMS integration
- Customizable logos and images
- Flexible theming system

### JavaScript Enhancements
- Auto-advance PIN input fields
- Password confirmation validation
- Form validation before submission
- MFA expiry timer with modal alert

## Template Integration

All templates are integrated with Thymeleaf and expect the following model attributes from controllers:

### Common Attributes (across most templates)
- `planName` - Organization/plan name
- `planFaviconPath` - Path to favicon
- `logo` - Path to logo image
- `contact` - Contact information HTML
- `error` - Error message to display
- `success` - Success message to display
- `footerLinks` - Footer links HTML
- `footerHTML` - Custom footer HTML
- `planPrimaryColor` - Primary theme color
- `planSecondaryColor` - Secondary theme color
- `planTertiaryColor` - Tertiary theme color

### Login Page Specific
- `intro` - Introduction section with title, summary, and items
- `banner` - Banner information
- `formTitle` - Form heading
- `usernameType` - "email" or "username"
- `allowForgotUsername` - Boolean for forgot username link
- `allowActivateAccount` - Boolean for activate account link
- `isBiometricEnabled` - Boolean for biometric auth
- `biometricType` - Type of biometric authentication

### MFA Pages Specific
- `progressSidebarSteps` - List of step names
- `progressSidebarLinks` - List of progress links
- `options` - List of factor options (for request-pin)
- `flow` - Current flow name ("login", "forgot-password", etc.)
- `mfaExpiryPinTime` - PIN expiry time in seconds

### Account Access Pages Specific
- `title` - Page title
- `subtitle` - Page subtitle
- `description` - Additional description
- `usernameType` - "email" or "username"
- `usernameLookupFields` - Fields for username lookup

### User Profile Pages Specific
- `firstName`, `lastName` - User name fields
- `email`, `confirmEmail` - Email fields
- `phoneNumber`, `secondaryPhoneNumber` - Phone fields
- `helpTexts` - Password requirement help texts

## Styling

The templates use a consistent design system with:

- **Primary Color**: #2d65b4 (customizable)
- **Secondary Color**: #1d3557 (customizable)
- **Font**: System font stack for optimal performance
- **Card Shadow**: Subtle elevation for depth
- **Border Radius**: 8px for rounded corners
- **Spacing**: Consistent 20px grid system

## Browser Support

Templates are tested and support:
- Chrome/Edge (latest 2 versions)
- Firefox (latest 2 versions)
- Safari (latest 2 versions)
- Mobile browsers (iOS Safari, Chrome Mobile)

## Development Notes

1. **Fragment Reuse**: Use `th:replace` to include common fragments
2. **Conditional Rendering**: Use `th:if` for optional content
3. **Dynamic URLs**: Use `@{/path}` for URL generation
4. **Model Attributes**: All attributes are null-safe with fallbacks
5. **CSS Custom Properties**: Used for theming and easy customization
6. **JavaScript**: Minimal, focused on UX enhancements only

## Testing

When testing templates:
1. Verify all model attributes are properly bound
2. Test with and without optional attributes
3. Test on different screen sizes
4. Verify accessibility with screen readers
5. Check color customization works correctly

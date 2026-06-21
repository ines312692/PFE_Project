import {SchemaField} from '../models/job.model';

export interface SchemaPreset {
  id: string;
  label: string;
  desc: string;
  color: 'blue' | 'green' | 'pink' | 'orange' | 'purple' | 'teal';
  icon: string;
  fields: SchemaField[];
}

const field = (
  name: string,
  selector: string,
  type: SchemaField['type'] = 'TEXT',
  required = true,
  attribute: string | null = null,
): SchemaField => ({name, selector, type, attribute, required});

export const SCHEMA_PRESETS: SchemaPreset[] = [
  {
    id: 'ecommerce',
    label: 'E-commerce',
    desc: 'Product name, price, rating, image & stock',
    color: 'blue',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
              <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
            </svg>`,
    fields: [
      field('title', 'h1.product-title, h1[data-testid="title"]'),
      field('price', '.price, [data-testid="price"], .product-price'),
      field('rating', '.rating, .stars, [data-testid="rating"]', 'TEXT', false),
      field('image', 'img.product-image, .gallery img', 'ATTRIBUTE', false, 'src'),
      field('availability', '.stock-status, .availability, [data-testid="stock"]', 'TEXT', false),
      field('sku', '[data-sku], .sku, .product-id', 'TEXT', false),
    ],
  },
  {
    id: 'news',
    label: 'News / Blog',
    desc: 'Title, author, date, body & category',
    color: 'orange',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2"/>
              <path d="M18 14h-8M15 18h-5M10 6h8v4h-8z"/>
            </svg>`,
    fields: [
      field('title', 'h1, h1.article-title, [data-testid="headline"]'),
      field('author', '.author, .byline, [rel="author"]', 'TEXT', false),
      field('date', 'time, .published-date, [datetime]', 'ATTRIBUTE', false, 'datetime'),
      field('category', '.category, .tag, .section-label', 'TEXT', false),
      field('summary', '.article-summary, .lead, .subtitle', 'TEXT', false),
      field('body', 'article p, .article-body, .post-content', 'HTML', false),
    ],
  },
  {
    id: 'jobs',
    label: 'Job Listings',
    desc: 'Title, company, location, salary & contract',
    color: 'green',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="2" y="7" width="20" height="14" rx="2"/>
              <path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/>
              <line x1="12" y1="12" x2="12" y2="17"/><line x1="9" y1="14.5" x2="15" y2="14.5"/>
            </svg>`,
    fields: [
      field('job_title', 'h1, .job-title, [data-testid="job-title"]'),
      field('company', '.company-name, .employer, [data-testid="company"]'),
      field('location', '.location, .job-location, [data-testid="location"]', 'TEXT', false),
      field('salary', '.salary, .compensation, [data-testid="salary"]', 'TEXT', false),
      field('contract', '.contract-type, .job-type, .employment-type', 'TEXT', false),
      field('description', '.job-description, .job-details, .description', 'TEXT', false),
    ],
  },
  {
    id: 'realestate',
    label: 'Real Estate',
    desc: 'Title, price, surface, rooms & address',
    color: 'teal',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z"/>
              <path d="M9 21V12h6v9"/>
            </svg>`,
    fields: [
      field('title', 'h1, .listing-title, .property-title'),
      field('price', '.price, .listing-price, [data-testid="price"]'),
      field('surface', '.surface, .area, .sqm', 'TEXT', false),
      field('rooms', '.rooms, .bedrooms, .pieces', 'TEXT', false),
      field('address', '.address, .location, .property-address', 'TEXT', false),
      field('image', '.listing-photo img, .property-image', 'ATTRIBUTE', false, 'src'),
    ],
  },
  {
    id: 'reviews',
    label: 'Reviews',
    desc: 'Reviewer, rating, date & comment',
    color: 'purple',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>`,
    fields: [
      field('reviewer', '.reviewer-name, .author, .user-name'),
      field('rating', '.rating, .stars, [data-score]', 'ATTRIBUTE', true, 'data-score'),
      field('date', 'time, .review-date, .date', 'TEXT', false),
      field('title', '.review-title, .review-heading', 'TEXT', false),
      field('comment', '.review-body, .review-text, .comment-content'),
    ],
  },
  {
    id: 'social',
    label: 'Social Media',
    desc: 'Username, content, stats & timestamp',
    color: 'pink',
    icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>`,
    fields: [
      field('username', '.username, .handle, [data-testid="username"]'),
      field('content', '.post-content, .tweet-text, .caption'),
      field('likes', '.likes-count, .like-count, [data-testid="likes"]', 'TEXT', false),
      field('shares', '.shares-count, .retweets, [data-testid="shares"]', 'TEXT', false),
      field('date', 'time, .timestamp, [datetime]', 'ATTRIBUTE', false, 'datetime'),
    ],
  },
];

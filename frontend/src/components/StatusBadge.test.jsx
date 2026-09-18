import { render, screen } from '@testing-library/react';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('renders status text', () => {
    render(<StatusBadge status="PROCESSED" />);
    expect(screen.getByText('PROCESSED')).toBeInTheDocument();
  });
});

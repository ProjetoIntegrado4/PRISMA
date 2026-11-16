"""Read-only audit; outputs aggregate counts without personal identifiers."""
import json
from collections import Counter
from datetime import date
from domain import rows, training_quality, day, norm

if __name__ == '__main__':
    records = rows('training_records')
    reasons = Counter(issue for row in records for issue in training_quality(row))
    report = {'date': str(date.today()), 'records': len(records),
              'distinct_courses': len({r.get('course_name') for r in records}),
              'status_counts': dict(Counter(r.get('completion_status') for r in records)),
              'records_with_warnings': sum(bool(training_quality(r)) for r in records),
              'warning_counts': dict(reasons)}
    print(json.dumps(report, ensure_ascii=False, indent=2))
